(ns starwars.schema
  "Star Wars example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic GraphQL schema generation
   - Enum support (Episode)
   - Multiple entity types (Human, Droid)
   - Reference relationships (friends)
   - Query resolvers with arguments")

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

;; All schemas map
(def schemas
  [human-schema
   droid-schema
   planet])