(ns starwars.schema
  "Simple User CRUD example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic GraphQL schema generation
   - Automatic CRUD mutations (create, update, delete)
   - Query resolvers with searchable fields"
  (:require [malli.core :as m]))

;; User schema with CRUD mutations
(def user-schema
  [:map
   {:ringline/datomic-ns "user"
    :ringline/query-root true
    :ringline/searchable [:email :name]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:email :string]
   [:age {:optional true} :int]])

;; All schemas map
(def schemas
  {:user user-schema})

;; Validation helpers
(defn validate-user
  "Validate user data"
  [data]
  (m/validate user-schema data))

