(ns ringline.core
  "High-level framework API for Malli-GraphQL integration.

   This namespace provides the main entry points for using the framework:
   - init-framework: Initialize with Malli schemas, get Datomic + Lacinia schemas
   - create-resolver: Create Lacinia resolver functions that use Datomic pull"
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [com.walmartlabs.lacinia.util :as util]
            [datomic.api :as d]
            [malli.core :as m]
            [malli.experimental.time :as met]
            [malli.registry :as mr]
            [ringline.mutation.executor :as mutation-executor]
            [ringline.mutation.lacinia :as mutation-lacinia]
            [ringline.mutation.parser :as mutation-parser]
            [ringline.query.converter :as converter]
            [ringline.response.transformer :as transformer]
            [ringline.schema.datomic :as datomic]
            [ringline.schema.lacinia :as lacinia]
            [ringline.schema.parser :as parser]
            [ringline.schema.scalars :as scalars]
            [ringline.schema.types :as types])
  (:import [datomic Connection]
           [datomic.db Db]
           [java.util UUID]))

(defn augment-context
  "Augment the Lacinia context with framework data for downstream resolvers."
  [context framework-data conn]
  (assoc context
         :ringline {:conn conn
                    :framework framework-data}))

(defn context->framework-data [context]
  (or (-> context :ringline :framework)
      (throw (ex-info "Lacinia context not augmented with framework data. Use ringline/augment-context to add it." {:context context}))))

(defn context->conn [context]
  (or (-> context :ringline :conn)
      (throw (ex-info "Lacinia context not augmented with Datomic connection. Use ringline/augment-context to add it." {:context context}))))

(defn parsed->by-schema-name
  [parsed]
  (->> parsed (map (juxt :schema-name identity)) (into {})))

(defn- create-entity-field-namespace-lookup
  ([parsed]
   (let [by-entity (parsed->by-schema-name parsed)]
     (->> by-entity
          (mapv (fn [[entity-name {:keys [fields properties]}]]
                  (->> fields
                       (map (fn [field]
                              (when-let [ref-to (-> field :properties :ringline/ref-to)]
                                [[entity-name (:name field)] (-> by-entity ref-to :properties :ringline/datomic-ns)])))
                       (remove nil?)
                       (vec)
                       (concat [[entity-name (-> properties :ringline/datomic-ns)]]))))
          (apply concat)
          (vec)
          (into {})))))

(defn- create-entity-field-reverse-lookups
  ([parsed]
   (->> (parsed->by-schema-name parsed)
        (mapv (fn [[entity-name {:keys [fields]}]]
                (->> fields
                     (map (fn [field]
                            (when-let [rev (-> field :properties :ringline/reverse-lookup)]
                              [[entity-name (:name field)] rev])))
                     (remove nil?)
                     (vec))))
        (apply concat)
        (vec)
        (into {}))))

(defn type->input-converter
  [type]
  (case type
    :uuid #(UUID/fromString %)
    nil))

(defn- create-entity-field-input-converters
  ([parsed]
   (->> (parsed->by-schema-name parsed)
        (mapv (fn [[entity-name {:keys [fields]}]]
                (->> fields
                     (map (fn [{:keys [name type]}]
                            (when-let [converter (type->input-converter type)]
                              [[entity-name name] converter])))
                     (remove nil?)
                     (vec))))
        (apply concat)
        (vec)
        (into {}))))

;; Malli Registry Setup

(defn- setup-malli-registry!
  "Set up Malli registry with custom schemas.

   Registers:
   - Default Malli schemas (built-in types)
   - Experimental time schemas (:time/local-date, :time/offset-date-time)
   - Custom Ringline schemas (:decimal)

   This function is called automatically by init-framework."
  []
  (mr/set-default-registry!
   (mr/composite-registry
    (m/default-schemas)
    (met/schemas)
    (scalars/custom-schemas)
    (mr/var-registry))))

(defn schemas->schemas-map [schemas]
  (->> schemas
       (map (fn [schema]
              (let [sn (-> schema
                           (m/properties)
                           :ringline/schema-name)]
                (when-not sn
                  (throw (ex-info "Schema must have :ringline/schema-name property" {:schema schema})))
                [sn schema])))
       (into {})))

;; Framework initialization
(defn init-framework
  "Initialize framework with Malli schemas.

   Orchestrates the complete schema processing pipeline:
   1. Parse all Malli schemas
   2. Generate Datomic attribute definitions
   3. Generate Lacinia GraphQL schema (queries + mutations)

   Args:
     schemas-map - Map of entity-name (keyword) to Malli schema
     options-map - Optional configuration:
                   :custom-operations - Map with :queries and :mutations for custom operations
                   :resolvers - Map of operation-name to resolver function

   Returns:
     Map with:
       :datomic - Vector of DatomicSchema maps (one per entity)
       :lacinia - Single merged LaciniaSchema map (with :queries and :mutations)
       :parsed - Vector of ParsedSchema maps (one per entity)
       :mutations - Vector of parsed mutation definitions (one per entity with mutations)

   Example:
     (init-framework {:User user-schema :Post post-schema}
                     {:custom-operations {:queries {:searchUsers {...}}}
                      :resolvers {:searchUsers search-users-fn}})"
  [schemas options-map]
  ;; Set up Malli registry with custom schemas
  (setup-malli-registry!)
  (try
    (let [schemas-map (schemas->schemas-map schemas)
          ;; Extract custom operations from options
          custom-operations (:custom-operations options-map)
          resolvers (:resolvers options-map)

          ;; Validate custom operations if present
          _ (when custom-operations
              (when-let [errors (types/validate-custom-operations custom-operations)]
                (throw (ex-info "Invalid custom operations"
                                {:custom-operations custom-operations
                                 :errors errors}))))

          ;; Parse all Malli schemas
          parsed-schemas (parser/parse-schemas schemas-map)

          ;; Generate Datomic schemas
          datomic-schemas (mapv datomic/generate-schema parsed-schemas)

          ;; Generate merged Lacinia schema (queries + custom operations)
          lacinia-schema (lacinia/generate-schemas parsed-schemas custom-operations)

          ;; Parse mutations from schemas
          mutation-defs (into []
                              (comp (map (fn [[entity-name schema]]
                                           (mutation-parser/parse-mutations entity-name schema)))
                                    (filter #(seq (:operations %))))
                              schemas-map)

          ;; Generate Lacinia mutation schemas
          mutation-schemas (mapv mutation-lacinia/generate-mutation-schemas mutation-defs)

          ;; Merge mutations into Lacinia schema
          lacinia-with-mutations (if (seq mutation-schemas)
                                   (assoc lacinia-schema
                                          :mutations (merge (:mutations lacinia-schema)
                                                            (apply merge {} (map :mutations mutation-schemas)))
                                          :input-objects (apply merge {} (map :input-objects mutation-schemas)))
                                   lacinia-schema)

          ;; Attach custom resolvers if provided
          lacinia-final (if resolvers
                          (lacinia/attach-resolvers lacinia-with-mutations resolvers)
                          lacinia-with-mutations)
          namespace-lookup (create-entity-field-namespace-lookup parsed-schemas)
          reverse-lookups (create-entity-field-reverse-lookups parsed-schemas)
          input-converters (create-entity-field-input-converters parsed-schemas)]
      {:datomic datomic-schemas
       :lacinia lacinia-final
       :parsed parsed-schemas
       :mutations mutation-defs
       :namespace-lookup namespace-lookup
       :reverse-lookups reverse-lookups
       :schemas-map schemas-map
       :input-converters input-converters})
    (catch Exception e
      (throw (ex-info "Failed to initialize framework"
                      {:schemas schemas
                       :options-map options-map
                       :error (.getMessage e)}
                      e)))))

(defn ensure-db [datomic-conn]
  (cond (instance? Db datomic-conn) datomic-conn
        (instance? Connection datomic-conn) (d/db datomic-conn)
        :else nil))

;; Resolver creation

(defn create-mutation-resolver
  "Create a Lacinia resolver function for mutations.

   The returned resolver function:
   1. Extracts mutation input from GraphQL args
   2. Validates input against Malli schema
   3. Converts to Datomic transaction
   4. Executes mutation and returns result

   Args:
     entity-type - Keyword representing the entity (e.g., :User)
     operation - The mutation operation (:create, :update, or :delete)
     datomic-conn - Datomic connection
     schema - The entity's Malli schema

   Returns:
     Function with signature (context, args, value) -> result
     Compatible with Lacinia resolver protocol

   Example:
     (create-mutation-resolver :User :create db-conn user-schema)"
  [entity-type operation datomic-conn schema namespace-lookup reverse-lookups input-converters]
  ;; Parse the schema once when creating the resolver
  (let [parsed-schema (parser/parse-schema entity-type schema)]
    (fn resolver [context args _value]
      (try
        ;; Extract input from args
        (let [input-data-raw (get args :input)
              ;; Convert string ID to UUID if present
              input-data (if-let [id-str (:id input-data-raw)]
                           (assoc input-data-raw :id (UUID/fromString id-str))
                           input-data-raw)
              entity-id (:id input-data)

              ;; Build mutation input map
              mutation-input {:operation operation
                              :entity-type (keyword (name entity-type))
                              :entity-id entity-id
                              :data input-data}

              ;; Execute mutation
              result (mutation-executor/execute-mutation mutation-input schema parsed-schema datomic-conn)]

          ;; Transform result for GraphQL
          (if (:success result)
            (case operation
              :delete
              ;; Delete returns boolean
              true

              ;; Create/Update return entity data
              ;; Convert UUID to string for GraphQL ID type
              (if-let [db (ensure-db datomic-conn)]
                (let [query-ctx (converter/build-query-context context entity-type args)
                      ;; Convert to Datomic pull pattern with where clauses
                      pull-result (converter/pull-with-args query-ctx namespace-lookup reverse-lookups input-converters)
                      pattern (:pattern pull-result)
                      {:keys [entity-id entity-type]} result
                      lookup-key [(keyword (entity-type namespace-lookup) "id") entity-id]
                      query-result (d/pull db pattern lookup-key)
                      transformed (transformer/transform-with-selections query-result query-ctx namespace-lookup)]
                  transformed)
                result))

            ;; On error, attach errors to Lacinia context and return nil
            ;; This allows GraphQL to return errors in the response without throwing
            (resolve/with-error context
              (first (:errors result)))))

        (catch Exception e
          ;; Re-throw to let Lacinia handle error formatting
          (throw e))))))

(defn construct-query [{:keys [pattern where-clauses]}]
  {:find [(list 'pull '?e pattern)]
   :where where-clauses})

(defn do-query [query conn]
  ;; Execute Datomic query
  (if conn
    (try (let [db (ensure-db conn)
               ;; Execute query with where clauses if present
               entities (->> (d/q query db)
                             (map first))]
           entities)
         (catch Exception e
           (throw (ex-info "Datomic query failed" {:query query}
                           e))))
    nil))

(defn transform-response [entities query-ctx namespace-lookup]
  (cond
    (= 1 (count entities)) (transformer/transform-with-selections (first entities) query-ctx namespace-lookup)
    (seq entities) (mapv #(transformer/transform-with-selections % query-ctx namespace-lookup) entities)
    :else nil))

(defn create-resolver
  "Create a Lacinia resolver function that uses Datomic pull.

   The returned resolver function:
   1. Extracts GraphQL selections from Lacinia context
   2. Converts to Datomic pull pattern
   3. Executes pull against Datomic database
   4. Transforms results to GraphQL format

   Args:
     entity-type - Keyword representing the entity (e.g., :User)
     datomic-conn - Datomic connection or database value
     parsed-schema - ParsedSchema map for this entity type

   Returns:
     Function with signature (context, args, value) -> result
     Compatible with Lacinia resolver protocol

   Example:
     (create-resolver :User db-conn parsed-user-schema)"
  ([entity-type]
   (create-resolver entity-type (fn [_context _args query] query)))
  ([entity-type query-interceptor]
   (fn resolver [context args _value]
     (try
       ;; Build query context from Lacinia, passing args explicitly
       (let [datomic-conn (context->conn context)
             {:keys [namespace-lookup reverse-lookups input-converters]} (context->framework-data context)
             query-ctx (converter/build-query-context context entity-type args)
             query-enter (if (and (map? query-interceptor) (:enter query-interceptor))
                           (:enter query-interceptor)
                           query-interceptor)
             query-leave (if (and (map? query-interceptor) (:leave query-interceptor))
                           (:leave query-interceptor)
                           (fn [_context _args query] query))
             ;; Convert to Datomic pull pattern with where clauses
             pull-result (->> (converter/pull-with-args query-ctx namespace-lookup reverse-lookups input-converters)
                              (construct-query)
                              (query-enter context args))]

         ;; Execute Datomic query
         (as-> pull-result x
           (do-query x datomic-conn)
           (transform-response x query-ctx namespace-lookup)
           (query-leave context args x)))

       (catch Exception e
         (throw (ex-info "Resolver execution failed"
                         {:entity-type entity-type
                          :args args
                          :error (.getMessage e)}
                         e)))))))

(defn attach-mutation-resolvers
  "Attach mutation resolvers to a Lacinia schema.

   For each mutation in the schema, creates and attaches a resolver function
   that executes the mutation against Datomic.

   Args:
     lacinia-schema - Lacinia schema map (with :mutations key)
     schemas-map - Map of entity-name (keyword) to Malli schema
     datomic-conn - Datomic connection

   Returns:
     Updated Lacinia schema with resolvers attached to all mutations

   Example:
     (attach-mutation-resolvers lacinia-schema {:user user-schema} db-conn)"
  ([lacinia-schema schemas datomic-conn namespace-lookup reverse-lookups input-converters]
   (if-let [mutations (:mutations lacinia-schema)]
     (let [;; For each mutation, attach a resolver 
           schemas-map (if (map? schemas) schemas (schemas->schemas-map schemas))
           mutations-with-resolvers
           (reduce-kv
            (fn [acc mutation-name mutation-def]
              ;; Parse mutation name to extract entity-type and operation
              ;; e.g., :createUser -> entity-type=:user, operation=:create
              (let [name-str (name mutation-name)
                    operation (cond
                                (.startsWith name-str "create") :create
                                (.startsWith name-str "update") :update
                                (.startsWith name-str "delete") :delete
                                :else nil)
                    entity-name (cond
                                  (.startsWith name-str "create") (subs name-str 6)
                                  (.startsWith name-str "update") (subs name-str 6)
                                  (.startsWith name-str "delete") (subs name-str 6)
                                  :else nil)
                    entity-key (when entity-name (keyword (str/lower-case entity-name)))
                    schema (get schemas-map entity-key)]

                (if (and operation entity-key schema)
                  ;; Create and attach resolver
                  (assoc acc mutation-name
                         (assoc mutation-def
                                :resolve (create-mutation-resolver
                                          entity-key
                                          operation
                                          datomic-conn
                                          schema
                                          namespace-lookup
                                          reverse-lookups
                                          input-converters)))
                  ;; No schema found, keep mutation as-is
                  (assoc acc mutation-name mutation-def))))
            {}
            mutations)]

       (assoc lacinia-schema :mutations mutations-with-resolvers))
     ;; No mutations in schema
     lacinia-schema)))

(defn create-query-resolver-map
  "Create a map of automatic query resolvers for a Lacinia schema."
  [parsed]
  (let [root-queries (->> parsed
                          (filter #(get-in % [:properties :ringline/query-root]))
                          (map :schema-name))]
    (->> root-queries
         (map (fn [query] [(keyword (str "queries/" (name query)))
                           (create-resolver query)]))
         (into {}))))

(defn auto-framework!
  "Calls init-framework and then transacts the Datomic schema and compiles the Lacinia schema with automatic resolvers."
  [conn schemas]
  (let [{:keys [datomic lacinia namespace-lookup parsed schemas-map reverse-lookups input-converters]
         :as framework} (init-framework schemas {})
        tx-data (mapcat datomic/schema->transaction datomic)
        query-resolvers (create-query-resolver-map parsed)
        schema (-> lacinia
                   ;; Attach resolvers
                   (util/inject-resolvers query-resolvers)
                   (attach-mutation-resolvers schemas-map conn namespace-lookup reverse-lookups input-converters)
                   (lacinia-schema/compile))]
    @(d/transact conn tx-data)
    (assoc framework :lacinia schema)))

(defn respond-with-error [context code message ex]
  (resolve/with-error context {:code code
                               :message (str message " " (if-let [cause (.getCause ex)]
                                                           (.getMessage cause)
                                                           (.getMessage ex)))
                               :exception ex}))

(defn pull-and-transform
  [context args entity-id entity-type]
  (let [datomic-conn (context->conn context)
        {:keys [namespace-lookup reverse-lookups input-converters]} (context->framework-data context)]
    (when-let [db (ensure-db datomic-conn)]
      (let [query-ctx (converter/build-query-context context entity-type args)
            ;; Convert to Datomic pull pattern with where clauses
            pull-result (converter/pull-with-args query-ctx namespace-lookup reverse-lookups input-converters)
            pattern (:pattern pull-result)
            query-result (d/pull db pattern entity-id)
            transformed (transformer/transform-with-selections query-result query-ctx namespace-lookup)]
        transformed))))

(defn transact-and-pull
  [tx-data-fn response-entity-type response-id]
  (fn [context args v]
    (try
      (let [datomic-conn (context->conn context)
            tx-data (tx-data-fn context args v)
            _tx-result (mutation-executor/execute-transaction datomic-conn tx-data)

            result-entity-id (cond
                               (keyword? response-id) (->> (map response-id tx-data)
                                                           (filter some?)
                                                           (first)
                                                           (conj [response-id])
                                                           (vec))
                               :else response-id)]
        (pull-and-transform context args result-entity-id response-entity-type))

      (catch Exception e
        (respond-with-error context :TRANSACTION_FAILED "Datomic transaction failed" e)))))


