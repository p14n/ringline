(ns ringline.core
  "High-level framework API for Malli-GraphQL integration.

   This namespace provides the main entry points for using the framework:
   - init-framework: Initialize with Malli schemas, get Datomic + Lacinia schemas
   - create-resolver: Create Lacinia resolver functions that use Datomic pull"
  (:require [ringline.schema.parser :as parser]
            [ringline.schema.datomic :as datomic]
            [ringline.schema.lacinia :as lacinia]
            [ringline.schema.scalars :as scalars]
            [ringline.schema.types :as types]
            [ringline.query.converter :as converter]
            [ringline.response.transformer :as transformer]
            [ringline.mutation.parser :as mutation-parser]
            [ringline.mutation.lacinia :as mutation-lacinia]
            [ringline.mutation.executor :as mutation-executor]
            [malli.core :as m]
            [malli.registry :as mr]
            [malli.util :as mu]
            [malli.experimental.time :as met]
            [datomic.api :as d]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [spyscope.core]))

(defn create-entity-field-namespace-lookup
  ([parsed]
   (let [by-entity (->> parsed (map (juxt :schema-name identity)) (into {}))
         lookups (->> by-entity
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
                      (into {}))]
     lookups)))

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
  [schemas-map options-map]
  ;; Set up Malli registry with custom schemas
  (setup-malli-registry!)
  (try
    (let [;; Extract custom operations from options
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
          namespace-lookup (create-entity-field-namespace-lookup parsed-schemas)]

      {:datomic datomic-schemas
       :lacinia lacinia-final
       :parsed parsed-schemas
       :mutations mutation-defs
       :namespace-lookup namespace-lookup})
    (catch Exception e
      (throw (ex-info "Failed to initialize framework"
                      {:schemas-map schemas-map
                       :options-map options-map
                       :error (.getMessage e)}
                      e)))))

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
  [entity-type operation datomic-conn schema namespace-lookup]
  ;; Parse the schema once when creating the resolver
  (let [parsed-schema (parser/parse-schema entity-type schema)]
    (fn resolver [context args _value]
      (try
        ;; Extract input from args
        (let [input-data-raw (get args :input)
              ;; Convert string ID to UUID if present
              input-data #spy/d (if-let [id-str (:id input-data-raw)]
                                  (assoc input-data-raw :id (java.util.UUID/fromString id-str))
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
              (let [query-ctx (converter/build-query-context context entity-type args)
                    ;; Convert to Datomic pull pattern with where clauses
                    pull-result (converter/pull-with-args query-ctx namespace-lookup)
                    pattern (:pattern pull-result)
                    db (if (instance? datomic.db.Db datomic-conn)
                         datomic-conn
                         (d/db datomic-conn))
                    {:keys [entity-id entity-type]} result
                    query-result (d/pull db pattern [(keyword (entity-type namespace-lookup) "id") entity-id])
                    transformed (transformer/transform-with-selections query-result query-ctx namespace-lookup)
                    _ (println "???????" result query-result)]
                ;(println "???????" result dbid query-result)
                transformed
                #_(merge (:data result)
                         {:id (str (:entity-id result))})))

            ;; On error, attach errors to Lacinia context and return nil
            ;; This allows GraphQL to return errors in the response without throwing
            (com.walmartlabs.lacinia.resolve/with-error context
              (first (:errors result)))))

        (catch Exception e
          ;; Re-throw to let Lacinia handle error formatting
          (throw e))))))

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
  ([entity-type datomic-conn]
   (create-resolver entity-type datomic-conn {}))
  ([entity-type datomic-conn namespace-lookup]
   (fn resolver [context args _value]
     (try
       ;; Build query context from Lacinia, passing args explicitly
       (let [query-ctx (converter/build-query-context context entity-type args)

             ;; Convert to Datomic pull pattern with where clauses
             pull-result (converter/pull-with-args query-ctx namespace-lookup)
             pattern (:pattern pull-result)
             where-clauses (:where-clauses pull-result)]

         ;; Execute Datomic query
         (if datomic-conn
           (let [db (if (instance? datomic.db.Db datomic-conn)
                      datomic-conn
                      (d/db datomic-conn))

                 ;; Execute query with where clauses if present
                 entities (if (seq where-clauses)
                            ;; Query with filtering
                            (let [query-result #spy/d (d/q {:find ['?e]
                                                            :where where-clauses}
                                                           db)
                                  entity-ids (map first query-result)]
                              (mapv #(d/pull db pattern %) entity-ids))
                            ;; No filtering - return empty for now
                            ;; (In real usage, would need entity-id from args)
                            [])]

             ;; Transform to GraphQL format
             (if (= 1 (count entities))
               (transformer/transform-with-selections (first entities) query-ctx namespace-lookup)
               (mapv #(transformer/transform-with-selections % query-ctx namespace-lookup) entities)))

           ;; No connection - return nil (useful for testing)
           nil))

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
  [lacinia-schema schemas-map datomic-conn namespace-lookup]
  (if-let [mutations (:mutations lacinia-schema)]
    (let [;; For each mutation, attach a resolver
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
                                         namespace-lookup)))
                 ;; No schema found, keep mutation as-is
                 (assoc acc mutation-name mutation-def))))
           {}
           mutations)]

      (assoc lacinia-schema :mutations mutations-with-resolvers))
    ;; No mutations in schema
    lacinia-schema))

;; Application entry point (preserved for compatibility)

(defn -main
  "Application entry point."
  [& args]
  (println "Ringline Malli-GraphQL Framework")
  (println "Use init-framework to get started"))

;; Rich comment block with REPL examples
(comment
  ;; Example 1: Basic framework initialization
  (require '[ringline.core :as core])
  (require '[malli.core :as m])

  (def user-schema
    [:map {:ringline/datomic-ns "user"
           :ringline/query-root true
           :ringline/searchable [:email :username]
           :ringline/mutations #{:create :update :delete}}
     [:id :uuid]
     [:email :string]
     [:username :string]
     [:created-at :int]
     [:posts [:vector :ref]]])

  (def post-schema
    [:map {:ringline/datomic-ns "post"
           :ringline/query-root true}
     [:id :uuid]
     [:title :string]
     [:content :string]
     [:author :ref]])

  ;; Initialize framework
  (def framework (core/init-framework {:user user-schema
                                       :post post-schema}
                                      {}))

  ;; Inspect results
  (:datomic framework)   ; => Vector of Datomic schemas
  (:lacinia framework)   ; => Merged Lacinia schema (with :queries and :mutations)
  (:parsed framework)    ; => Vector of parsed schemas
  (:mutations framework) ; => Vector of mutation definitions

  ;; Example 2: Create resolvers
  (def db-conn nil)  ; Your Datomic connection here
  (def parsed-user (first (filter #(= :user (:schema-name %)) (:parsed framework))))

  (def user-resolver (core/create-resolver :User db-conn parsed-user))

  ;; Use resolver in Lacinia schema
  (def lacinia-with-resolvers
    (update-in (:lacinia framework) [:queries :user :resolve]
               (constantly user-resolver)))

  ;; Example 3: Complete workflow
  (def schemas {:user user-schema :post post-schema})
  (def fw (core/init-framework schemas {}))

  ;; Get Datomic transaction data
  (require '[ringline.schema.datomic :as datomic])
  (def tx-data (mapcat datomic/schema->transaction (:datomic fw)))

  ;; Transact to Datomic
  ;; @(d/transact conn tx-data)

  ;; Compile Lacinia schema
  (require '[com.walmartlabs.lacinia.schema :as schema])
  ;; (def compiled-schema (schema/compile lacinia-with-resolvers))

  ;; Example 4: Working with mutations
  (def user-with-mutations
    [:map {:ringline/datomic-ns "user"
           :ringline/query-root true
           :ringline/mutations #{:create :update :delete}}
     [:id :uuid]
     [:username :string]
     [:email :string]
     [:created-at :int]])

  (def fw-with-mutations (core/init-framework {:user user-with-mutations} {}))

  ;; Check mutations were generated
  (get-in fw-with-mutations [:lacinia :mutations])
  ;; => {:createUser {...}, :updateUser {...}, :deleteUser {...}}

  (get-in fw-with-mutations [:lacinia :input-objects])
  ;; => {:CreateUserInput {...}, :UpdateUserInput {...}}

  ;; Attach mutation resolvers
  (def lacinia-with-mutation-resolvers
    (core/attach-mutation-resolvers
     (:lacinia fw-with-mutations)
     {:user user-with-mutations}
     db-conn
     {}))

  ;; Create individual mutation resolver
  (def create-user-resolver
    (core/create-mutation-resolver :user :create db-conn user-with-mutations {}))

  ;; Use resolver in Lacinia
  ;; (create-user-resolver context {:input {:username "alice" :email "alice@example.com"}} nil)

  ;; ============================================================================
  ;; Custom Operations Example (Root-Level API)
  ;; ============================================================================

  ;; Define entity schemas (no custom operations in properties)
  (def user-schema
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true
           :ringline/searchable-fields [:username :email]}
     [:id :uuid]
     [:username :string]
     [:email :string]
     [:role :string]])

  (def order-schema
    [:map {:ringline/datomic-ns :order
           :ringline/query-root true}
     [:id :uuid]
     [:user-id {:ringline/ref-to :user} :uuid]
     [:status :string]
     [:total :int]])

  ;; Define custom operations separately at root level
  (def custom-operations
    {:queries {:searchUsers {:args [:map
                                    [:query :string]
                                    [:role {:optional true} :string]]
                             :return-type :User
                             :description "Search users by query string and optional role"}
               :userStats {:args [:map [:user-id :uuid]]
                           :return-type :User
                           :description "Get user statistics"}}
     :mutations {:approveOrder {:args [:map
                                       [:order-id :uuid]
                                       [:notes {:optional true} :string]]
                                :return-type :Order
                                :description "Approve an order with optional notes"}
                 :banUser {:args [:map [:user-id :uuid]]
                           :return-type :User
                           :description "Ban a user"}}})

  ;; Define custom resolver functions
  (defn search-users-resolver [ctx args value]
    ;; Implementation would query database
    [{:id "user-1" :username "alice" :email "alice@example.com" :role "admin"}
     {:id "user-2" :username "bob" :email "bob@example.com" :role "user"}])

  (defn user-stats-resolver [ctx args value]
    ;; Implementation would aggregate user data
    {:id (:user-id args)
     :username "alice"
     :email "alice@example.com"
     :role "admin"})

  (defn approve-order-resolver [ctx args value]
    ;; Implementation would update order status
    {:id (:order-id args)
     :status "approved"
     :total 100
     :user-id "user-1"})

  (defn ban-user-resolver [ctx args value]
    ;; Implementation would ban user
    {:id (:user-id args)
     :username "banned-user"
     :email "banned@example.com"
     :role "banned"})

  ;; Initialize framework with custom operations
  (def fw-with-custom-ops
    (init-framework
     {:User user-schema
      :Order order-schema}
     {:custom-operations custom-operations
      :resolvers {:searchUsers search-users-resolver
                  :userStats user-stats-resolver
                  :approveOrder approve-order-resolver
                  :banUser ban-user-resolver}}))

  ;; Inspect the generated Lacinia schema
  (:lacinia fw-with-custom-ops)
  ;; => {:objects {...}
  ;;     :queries {:user {...}           ; auto-generated
  ;;               :order {...}          ; auto-generated
  ;;               :searchUsers {...}    ; custom
  ;;               :userStats {...}}     ; custom
  ;;     :mutations {:approveOrder {...} ; custom
  ;;                 :banUser {...}}     ; custom
  ;;     :scalars {...}}

  ;; Custom operations override auto-generated operations with same name
  (def custom-ops-override
    {:queries {:user {:args [:map [:id :uuid]]
                      :return-type :User
                      :description "Custom user query (overrides auto-generated)"}}})

  (def fw-with-override
    (init-framework
     {:User user-schema}
     {:custom-operations custom-ops-override
      :resolvers {:user (fn [ctx args value]
                          {:id (:id args)
                           :username "custom-lookup"
                           :email "custom@example.com"
                           :role "user"})}}))

  ;; The :user query now uses the custom definition instead of auto-generated
  (get-in fw-with-override [:lacinia :queries :user :description])
  ;; => "Custom user query (overrides auto-generated)"
  )

