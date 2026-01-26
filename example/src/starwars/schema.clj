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
  [:map {:ringline/datomic-ns "planet"}
   [:id :uuid]
   [:name :string]])

;; Human schema - represents a human character in Star Wars
(def human-schema
  [:map
   {:ringline/datomic-ns "human"
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}
    :ringline/searchable [:id]}
   [:id :string]
   [:name :string]
   [:home_planet {:optional true :ringline/ref-to :planet} #'planet]])

;; Droid schema - represents a droid character in Star Wars
(def droid-schema
  [:map
   {:ringline/datomic-ns "droid"
    :ringline/query-root true
    :ringline/searchable [:id]}
   [:id :string]
   [:name :string]
   [:primary_function {:optional true} :string]])

;; All schemas map
(def schemas
  {:human human-schema
   :droid droid-schema
   :planet planet})