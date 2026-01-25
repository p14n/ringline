(ns starwars.schema
  "Star Wars example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic GraphQL schema generation
   - Enum support (Episode)
   - Multiple entity types (Human, Droid)
   - Reference relationships (friends)
   - Query resolvers with arguments"
  (:require [malli.core :as m]))

;; Episode enum - the episodes of the original Star Wars trilogy
(def episode-enum
  [:enum :NEWHOPE :EMPIRE :JEDI])

;; Human schema - represents a human character in Star Wars
(def human-schema
  [:map
   {:ringline/datomic-ns "human"
    :ringline/query-root true
    :ringline/searchable [:id]}
   [:id :string]
   [:name :string]
   [:appears_in [:vector episode-enum]]
   [:home_planet {:optional true} :string]])

;; Droid schema - represents a droid character in Star Wars
(def droid-schema
  [:map
   {:ringline/datomic-ns "droid"
    :ringline/query-root true
    :ringline/searchable [:id]}
   [:id :string]
   [:name :string]
   [:appears_in [:vector episode-enum]]
   [:primary_function {:optional true} :string]])

;; All schemas map
(def schemas
  {:human human-schema
   :droid droid-schema})

;; Validation helpers
(defn validate-human
  "Validate human data"
  [data]
  (m/validate human-schema data))

(defn validate-droid
  "Validate droid data"
  [data]
  (m/validate droid-schema data))

