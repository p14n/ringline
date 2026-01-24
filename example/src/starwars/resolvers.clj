(ns starwars.resolvers
  "GraphQL resolvers for User CRUD example.

   Query resolvers for the User entity."
  (:require [starwars.db :as db]))

;; Helper to convert UUIDs to strings for GraphQL
(defn uuid->string
  "Convert UUID to string for GraphQL ID type"
  [user]
  (when user
    (update user :id str)))

;; Query resolver for User entity
(defn resolve-user
  "Resolver for user query. Supports searching by email or name."
  [context args value]
  (let [user (cond
               ;; Search by email
               (:email args)
               (first (filter #(= (:email %) (:email args)) (db/get-all-users)))

               ;; Search by name
               (:name args)
               (first (filter #(= (:name %) (:name args)) (db/get-all-users)))

               ;; No search criteria - return all users (or first user)
               :else
               (first (db/get-all-users)))]
    ;; Convert UUID to string for GraphQL
    (uuid->string user)))

;; Resolver map for Lacinia
;; The key should match the placeholder resolver keyword (e.g., :resolve-user)
;; Use #' to get the var instead of the function value
(def resolvers
  {:resolve-user #'resolve-user})

