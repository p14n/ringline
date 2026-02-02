(ns starwars.auto
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
            [datomic.api :as d]))

(def planet
  [:map {:ringline/schema-name :planet
         :ringline/datomic-ns "planet"}
   [:id :string]
   [:name :string]])

;; Human schema - represents a human character in Star Wars
(def human-schema
  [:map
   {:ringline/schema-name :human
    :ringline/datomic-ns "human"
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}
    :ringline/searchable [:id :name]}
   [:id :uuid]
   [:name :string]
   [:home_planet {:optional true} #'planet]])

;; Droid schema - represents a droid character in Star Wars
(def droid-schema
  [:map
   {:ringline/schema-name :droid
    :ringline/datomic-ns "droid"
    :ringline/query-root true
    :ringline/searchable [:id]}
   [:id :string]
   [:name :string]
   [:primary_function {:optional true} :string]])

(def schemas [human-schema droid-schema planet])


(def initial-planets [{:db/id "Tatooine" :planet/id "TTOO" :planet/name "Tatooine"}
                      {:db/id "Alderaan" :planet/id "ALDE" :planet/name "Alderaan"}])
(def initial-humans [{:db/id (d/tempid :db.part/user) :human/id (d/squuid)
                      :human/name "Luke Skywalker" :human/home_planet "Tatooine"}])

(def initial-droids [{:db/id (d/tempid :db.part/user) :droid/id "2000"
                      :droid/name "R2-D2" :droid/primary_function "Astromech"}])

(def db-uri "datomic:mem://starwars-auto")

(defn create-graphql-system!
  "Create and initialize Datomic in-memory database with schema"
  [db-uri]
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")
    (let [{:keys [lacinia] :as framework} (ringline/auto-framework! conn schemas)]

      @(d/transact conn (concat initial-planets initial-humans initial-droids))

      {:lacinia lacinia
       :framework framework
       :conn conn})))

