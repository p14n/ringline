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
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [com.walmartlabs.lacinia.util :as util]
            [datomic.api :as d]
            [ringline.core :as core]))

;; Datomic database setup
(def db-uri "datomic:mem://starwars-example")

(def initial-planets [{:db/id "Tatooine"
                       :planet/name "Tatooine"}
                      {:db/id "Alderaan"
                       :planet/name "Alderaan"}])

(def initial-humans [{:db/id (d/tempid :db.part/user)
                      :human/id "1000"
                      :human/name "Luke Skywalker"
                      :human/home_planet "Tatooine"}
                     {:db/id (d/tempid :db.part/user)
                      :human/id "1001"
                      :human/name "Darth Vader"
                      :human/home_planet "Tatooine"}
                     {:db/id (d/tempid :db.part/user)
                      :human/id "1002"
                      :human/name "Han Solo"}
                     {:db/id (d/tempid :db.part/user)
                      :human/id "1003"
                      :human/name "Leia Organa"
                      :human/home_planet "Alderaan"}])

(def initial-droids [{:db/id (d/tempid :db.part/user)
                      :droid/id "2000"
                      :droid/name "R2-D2"
                      :droid/primary_function "Astromech"}
                     {:db/id (d/tempid :db.part/user)
                      :droid/id "2001"
                      :droid/name "C-3PO"
                      :droid/primary_function "Protocol"}])

(defn create-graphql-system!
  "Create and initialize Datomic in-memory database with schema"
  []
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")
    (let [{:keys [datomic lacinia]} (ringline/init-framework schema/schemas {})
          tx-data (mapcat ringline-datomic/schema->transaction datomic)
          schema (-> lacinia
                     ;; Attach resolvers
                     (util/inject-resolvers {:queries/human (ringline/create-resolver :human conn)
                                             :queries/droid (ringline/create-resolver :droid conn)})
                     (core/attach-mutation-resolvers schema/schemas conn)
                     (lacinia-schema/compile))]

      @(d/transact conn tx-data)
      @(d/transact conn (concat initial-planets initial-humans initial-droids))

      {:schema schema
       :conn conn})))

(defn cleanup-database!
  "Clean up the database connection"
  [conn]
  (d/release conn)
  (d/delete-database db-uri))


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


