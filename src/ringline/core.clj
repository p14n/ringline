(ns ringline.core
  "High-level framework API for Malli-GraphQL integration.

   This namespace provides the main entry points for using the framework:
   - init-framework: Initialize with Malli schemas, get Datomic + Lacinia schemas
   - create-resolver: Create Lacinia resolver functions that use Datomic pull"
  (:require [ringline.schema.parser :as parser]
            [ringline.schema.datomic :as datomic]
            [ringline.schema.lacinia :as lacinia]
            [ringline.query.converter :as converter]
            [ringline.response.transformer :as transformer]
            [datomic.api :as d]))

;; Framework initialization

(defn init-framework
  "Initialize framework with Malli schemas.

   Orchestrates the complete schema processing pipeline:
   1. Parse all Malli schemas
   2. Generate Datomic attribute definitions
   3. Generate Lacinia GraphQL schema

   Args:
     schemas-map - Map of entity-name (keyword) to Malli schema
     options-map - Optional configuration (currently unused, reserved for future)

   Returns:
     Map with:
       :datomic - Vector of DatomicSchema maps (one per entity)
       :lacinia - Single merged LaciniaSchema map
       :parsed - Vector of ParsedSchema maps (one per entity)

   Example:
     (init-framework {:User user-schema :Post post-schema} {})"
  [schemas-map options-map]
  (try
    (let [;; Parse all Malli schemas
          parsed-schemas (parser/parse-schemas schemas-map)

          ;; Generate Datomic schemas
          datomic-schemas (mapv datomic/generate-schema parsed-schemas)

          ;; Generate merged Lacinia schema
          lacinia-schema (lacinia/generate-schemas parsed-schemas)]

      {:datomic datomic-schemas
       :lacinia lacinia-schema
       :parsed parsed-schemas})
    (catch Exception e
      (throw (ex-info "Failed to initialize framework"
                      {:schemas-map schemas-map
                       :options-map options-map
                       :error (.getMessage e)}
                      e)))))

;; Resolver creation

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
  [entity-type datomic-conn parsed-schema]
  (fn resolver [context args value]
    (try
      ;; Build query context from Lacinia
      (let [query-ctx (converter/build-query-context context entity-type)

            ;; Convert to Datomic pull pattern with where clauses
            pull-result (converter/pull-with-args query-ctx)
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
                           (let [query-result (d/q {:find ['?e]
                                                     :where where-clauses}
                                                    db)
                                 entity-ids (map first query-result)]
                             (mapv #(d/pull db pattern %) entity-ids))
                           ;; No filtering - return empty for now
                           ;; (In real usage, would need entity-id from args)
                           [])]

            ;; Transform to GraphQL format
            (if (= 1 (count entities))
              (transformer/transform-with-selections (first entities) query-ctx)
              (mapv #(transformer/transform-with-selections % query-ctx) entities)))

          ;; No connection - return nil (useful for testing)
          nil))

      (catch Exception e
        (throw (ex-info "Resolver execution failed"
                        {:entity-type entity-type
                         :args args
                         :error (.getMessage e)}
                        e))))))

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
           :ringline/searchable [:email :username]}
     [:id :uuid]
     [:email :string]
     [:username :string]
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
  (:datomic framework)  ; => Vector of Datomic schemas
  (:lacinia framework)  ; => Merged Lacinia schema
  (:parsed framework)   ; => Vector of parsed schemas

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

  )

