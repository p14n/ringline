(ns starwars.core
  "Star Wars example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Automatic query resolvers via :ringline/query-root
   - Enum support (Episode)
   - Multiple entity types (Human, Droid)
   - Datomic in-memory database"
  (:require [ringline.core :as ringline]
            [ringline.schema.datomic :as ringline-datomic]
            [starwars.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [datomic.api :as d]
            [clojure.pprint :as pprint]))

;; Datomic database setup
(def db-uri "datomic:mem://starwars-example")

;; Character IDs (UUIDs for internal storage)
(def luke-id #uuid "00000000-0000-0000-0000-000000000001")
(def vader-id #uuid "00000000-0000-0000-0000-000000000002")
(def han-id #uuid "00000000-0000-0000-0000-000000000003")
(def leia-id #uuid "00000000-0000-0000-0000-000000000004")
(def r2d2-id #uuid "00000000-0000-0000-0000-000000000005")
(def c3po-id #uuid "00000000-0000-0000-0000-000000000006")

;; Mapping from string IDs to UUIDs
(def id->uuid
  {"1000" luke-id
   "1001" vader-id
   "1002" han-id
   "1003" leia-id
   "2000" r2d2-id
   "2001" c3po-id})

;; Mapping from UUIDs to string IDs
(def uuid->id
  (into {} (map (fn [[k v]] [v k]) id->uuid)))

(defn create-database!
  "Create and initialize Datomic in-memory database with schema"
  []
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")
    (let [framework (ringline/init-framework schema/schemas {})
          datomic-schemas (:datomic framework)
          tx-data (mapcat ringline-datomic/schema->transaction datomic-schemas)]

      @(d/transact conn tx-data)

      ;; Add initial Star Wars characters
      (let [initial-humans [{:db/id (d/tempid :db.part/user)
                             :human/id "1000"
                             :human/name "Luke Skywalker"
                             :human/appears_in [:NEWHOPE :EMPIRE :JEDI]
                             :human/home_planet "Tatooine"}
                            {:db/id (d/tempid :db.part/user)
                             :human/id "1001"
                             :human/name "Darth Vader"
                             :human/appears_in [:NEWHOPE :EMPIRE :JEDI]
                             :human/home_planet "Tatooine"}
                            {:db/id (d/tempid :db.part/user)
                             :human/id "1002"
                             :human/name "Han Solo"
                             :human/appears_in [:NEWHOPE :EMPIRE :JEDI]}
                            {:db/id (d/tempid :db.part/user)
                             :human/id "1003"
                             :human/name "Leia Organa"
                             :human/appears_in [:NEWHOPE :EMPIRE :JEDI]
                             :human/home_planet "Alderaan"}]
            initial-droids [{:db/id (d/tempid :db.part/user)
                             :droid/id "2000"
                             :droid/name "R2-D2"
                             :droid/appears_in [:NEWHOPE :EMPIRE :JEDI]
                             :droid/primary_function "Astromech"}
                            {:db/id (d/tempid :db.part/user)
                             :droid/id "2001"
                             :droid/name "C-3PO"
                             :droid/appears_in [:NEWHOPE :EMPIRE :JEDI]
                             :droid/primary_function "Protocol"}]]
        @(d/transact conn (concat initial-humans initial-droids)))

      conn)))

(defn cleanup-database!
  "Clean up the database connection"
  [conn]
  (d/release conn)
  (d/delete-database db-uri))

;; Note: We don't need custom resolvers for human and droid queries!
;; Ringline's automatic resolvers (via :ringline/query-root true) handle these.
;; We only need custom resolvers for queries that Ringline doesn't support,
;; like the hero query which requires a Character interface/union.

;; Create the complete GraphQL schema with resolvers
(defn create-schema
  "Create the complete Lacinia schema with resolvers attached and compiled"
  [conn]
  (let [framework (ringline/init-framework schema/schemas {})
        lacinia-schema (:lacinia framework)

        ;; Add Episode enum to schema
        schema-with-enum (assoc-in lacinia-schema [:enums :Episode]
                                   {:description "The episodes of the original Star Wars trilogy."
                                    :values [:NEWHOPE :EMPIRE :JEDI]})

        ;; Note: The original Lacinia Star Wars schema uses a Character interface,
        ;; but Ringline doesn't support interfaces yet. For this example, we'll
        ;; skip the hero query and focus on the human and droid queries.

        ;; Attach Ringline's automatic resolvers for human and droid queries
        ;; These are generated automatically because of :ringline/query-root true
        human-resolver (ringline/create-resolver :human conn)
        droid-resolver (ringline/create-resolver :droid conn)

        schema-with-resolvers (-> schema-with-enum
                                  ;; Attach resolvers
                                  (assoc-in [:queries :human :resolve] human-resolver)
                                  (assoc-in [:queries :droid :resolve] droid-resolver)
                                  ;; Add default values for query arguments
                                  (assoc-in [:queries :human :args :id :default-value] "1001")
                                  (assoc-in [:queries :droid :args :id :default-value] "2001"))]
    ;; Compile the schema
    (lacinia-schema/compile schema-with-resolvers)))

;; Execute a GraphQL query
(defn execute-query
  "Execute a GraphQL query against the schema with Datomic connection in context"
  [schema query-string conn]
  (lacinia/execute schema query-string nil {:conn conn}))

(defn -main
  "Main entry point for the example"
  [& args]
  (println "\n=== Ringline Star Wars Example ===\n")
  (println "This example demonstrates the Ringline framework with the classic Star Wars schema.")
  (println "")
  (println "To see the framework in action, run the tests:")
  (println "  # Run example tests only:")
  (println "  clojure -M:test --focus :example")
  (println "")
  (println "  # Run all framework tests (including example):")
  (println "  clojure -M:test")
  (println "")
  (println "Or explore the code in the REPL using the examples in the comment block below.")
  (println ""))

(comment
  ;; REPL examples

  ;; Create database and schema
  (def conn (create-database!))
  (def schema (create-schema conn))

  ;; Query for the hero (defaults to Luke)
  (execute-query schema
                 "{ hero { id name } }"
                 conn)

  ;; Query for the hero of EMPIRE (R2-D2)
  (execute-query schema
                 "{ hero(episode: EMPIRE) { id name } }"
                 conn)

  ;; Query for a human by ID (Darth Vader is the default)
  (execute-query schema
                 "{ human { id name home_planet } }"
                 conn)

  ;; Query for Luke Skywalker
  (execute-query schema
                 "{ human(id: \"1000\") { id name home_planet appears_in } }"
                 conn)

  ;; Query for a droid by ID (C-3PO is the default)
  (execute-query schema
                 "{ droid { id name primary_function } }"
                 conn)

  ;; Query for R2-D2
  (execute-query schema
                 "{ droid(id: \"2000\") { id name primary_function appears_in } }"
                 conn)

  ;; Query the database directly with Datalog
  (d/q '[:find ?name ?type
         :where
         (or [?e :human/name ?name]
             [?e :droid/name ?name])
         [(ground "character") ?type]]
       (d/db conn))

  ;; Clean up
  (d/release conn)
  (d/delete-database db-uri))

