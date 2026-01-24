(ns starwars.core
  "Star Wars example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Enum support (Episode)
   - Multiple entity types (Human, Droid)
   - Reference relationships (friends)
   - Custom query resolvers
   - Datomic in-memory database"
  (:require [ringline.core :as ringline]
            [ringline.schema.datomic :as ringline-datomic]
            [ringline.schema.parser :as parser]
            [ringline.response.transformer :as transformer]
            [starwars.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [com.walmartlabs.lacinia.resolve :as resolve]
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

;; Custom resolvers for Star Wars queries

(defn resolve-hero
  "Resolve the hero query - returns R2-D2 for EMPIRE, Luke for others"
  [context args value]
  (let [conn (get context :conn)
        db (d/db conn)
        episode (:episode args)
        ;; R2-D2 is the hero of EMPIRE, Luke is the hero of other episodes
        hero-id (if (= episode :EMPIRE) "2000" "1000")
        ;; Try to find in droids first, then humans
        droid-result (d/q '[:find (pull ?e [*]) .
                            :in $ ?id
                            :where [?e :droid/id ?id]]
                          db hero-id)
        human-result (when-not droid-result
                       (d/q '[:find (pull ?e [*]) .
                              :in $ ?id
                              :where [?e :human/id ?id]]
                            db hero-id))]
    (or droid-result human-result)))

(defn resolve-human
  "Resolve the human query by ID"
  [context args value]
  (let [conn (get context :conn)
        db (d/db conn)
        id (:id args)
        parsed-schema (parser/parse-schema :human schema/human-schema)
        result (d/q '[:find (pull ?e [*]) .
                      :in $ ?id
                      :where [?e :human/id ?id]]
                    db id)]
    (when result
      (transformer/datomic->graphql result parsed-schema))))

(defn resolve-droid
  "Resolve the droid query by ID"
  [context args value]
  (let [conn (get context :conn)
        db (d/db conn)
        id (:id args)
        parsed-schema (parser/parse-schema :droid schema/droid-schema)
        result (d/q '[:find (pull ?e [*]) .
                      :in $ ?id
                      :where [?e :droid/id ?id]]
                    db id)]
    (when result
      (transformer/datomic->graphql result parsed-schema))))

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

        ;; Update human and droid queries with proper args and defaults
        schema-with-resolvers (-> schema-with-enum
                                  (assoc-in [:queries :human :type] '(non-null :Human))
                                  (assoc-in [:queries :human :resolve] resolve-human)
                                  (assoc-in [:queries :human :args] {:id {:type 'String
                                                                           :default-value "1001"}})
                                  (assoc-in [:queries :droid :type] :Droid)
                                  (assoc-in [:queries :droid :resolve] resolve-droid)
                                  (assoc-in [:queries :droid :args] {:id {:type 'String
                                                                           :default-value "2001"}}))]
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

