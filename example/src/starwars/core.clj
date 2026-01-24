(ns starwars.core
  "User CRUD example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic GraphQL schema generation
   - Automatic CRUD mutations (create, update, delete)
   - Query resolvers with searchable fields
   - In-memory database"
  (:require [ringline.core :as ringline]
            [starwars.schema :as schema]
            [starwars.db :as db]
            [starwars.resolvers :as resolvers]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [com.walmartlabs.lacinia.util :as lacinia-util]
            [clojure.pprint :as pprint]))

;; Mock Datomic connection that uses the in-memory database
;; This simulates Datomic's transact function
(def mock-db-conn
  {:db-type :mock
   :db-after db/users  ; Reference to the atom
   :transact-fn (fn [tx-data]
                  ;; Process transaction data
                  ;; tx-data is a vector of maps with Datomic transaction format
                  (doseq [tx tx-data]
                    (cond
                      ;; Retract entity (delete)
                      (and (vector? tx) (= :db/retractEntity (first tx)))
                      (let [[_ [_ id]] tx]
                        (db/delete-user! id))

                      ;; Add/update entity
                      (map? tx)
                      (let [;; Extract ID - could be direct value or lookup ref
                            db-id (get tx :db/id)
                            id (if (vector? db-id)
                                 ;; Lookup ref: [:user/id uuid]
                                 (second db-id)
                                 ;; Direct ID or tempid
                                 (get tx (keyword "user" "id")))
                            ;; Convert namespaced keywords to simple keywords
                            data (into {}
                                       (keep (fn [[k v]]
                                               (when-not (= k :db/id)
                                                 [(keyword (name k)) v]))
                                             tx))]
                        (if (and id (db/get-user id))
                          (db/update-user! id data)
                          (db/create-user! (assoc data :id id))))))
                  {:db-after db/users
                   :tx-data tx-data
                   :tempids {}})})

;; Initialize the Ringline framework
(defn init-framework
  "Initialize the Ringline framework with User schemas"
  []
  (ringline/init-framework schema/schemas {}))

;; Create the complete GraphQL schema with resolvers
(defn create-schema
  "Create the complete Lacinia schema with resolvers attached and compiled"
  []
  (let [framework (init-framework)
        lacinia-schema (:lacinia framework)

        ;; Attach mutation resolvers
        schema-with-mutations (ringline/attach-mutation-resolvers
                               lacinia-schema
                               schema/schemas
                               mock-db-conn)

        ;; Attach query resolver
        schema-with-all-resolvers (assoc-in schema-with-mutations
                                            [:queries :user :resolve]
                                            resolvers/resolve-user)]
    ;; Compile the schema
    (lacinia-schema/compile schema-with-all-resolvers)))

;; Execute a GraphQL query
(defn execute-query
  "Execute a GraphQL query against the schema"
  [schema query-string]
  (lacinia/execute schema query-string nil nil))

;; Example queries
(defn example-queries
  "Run example queries to demonstrate the system"
  []
  (println "\n=== Initializing User Database ===")
  (db/init-db!)
  (println "Database initialized with sample data")

  (println "\n=== Creating GraphQL Schema ===")
  (let [schema (create-schema)]
    (println "Schema created successfully")

    (println "\n=== Example Query: Search User by Email ===")
    (let [result (execute-query schema
                               "{ user(email: \"alice@example.com\") { id name email age } }")]
      (pprint/pprint result))

    (println "\n=== Example Query: Search User by Name ===")
    (let [result (execute-query schema
                               "{ user(name: \"Bob Smith\") { id name email age } }")]
      (pprint/pprint result))

    (println "\n=== Example Mutation: Create New User ===")
    (let [result (execute-query schema
                               "mutation {
                                  createUser(input: {
                                    name: \"Diana Prince\"
                                    email: \"diana@example.com\"
                                    age: 28
                                  }) {
                                    id
                                    name
                                    email
                                    age
                                  }
                                }")]
      (pprint/pprint result))

    (println "\n=== Example Mutation: Update User ===")
    (let [result (execute-query schema
                               "mutation {
                                  updateUser(input: {
                                    id: \"00000000-0000-0000-0000-000000000002\"
                                    age: 26
                                  }) {
                                    id
                                    name
                                    email
                                    age
                                  }
                                }")]
      (pprint/pprint result))

    (println "\n=== Verify Update: Query Updated User ===")
    (let [result (execute-query schema
                               "{ user(email: \"bob@example.com\") { id name email age } }")]
      (pprint/pprint result))

    (println "\n=== Example Mutation: Delete User ===")
    (let [result (execute-query schema
                               "mutation {
                                  deleteUser(input: {
                                    id: \"00000000-0000-0000-0000-000000000003\"
                                  })
                                }")]
      (pprint/pprint result))

    (println "\n=== All Examples Complete ===\n")))

(defn -main
  "Main entry point for the example"
  [& args]
  (example-queries))

(comment
  ;; REPL examples

  ;; Initialize database
  (db/init-db!)

  ;; Create schema
  (def schema (create-schema))

  ;; Query for a user by ID
  (execute-query schema
                 "{ user(id: \"00000000-0000-0000-0000-000000000001\") { id name email age } }")

  ;; Query for a user by email
  (execute-query schema
                 "{ userByEmail(email: \"bob@example.com\") { id name email age } }")

  ;; Create a new user
  (execute-query schema
                 "mutation {
                    createUser(input: {
                      name: \"Eve Wilson\"
                      email: \"eve@example.com\"
                      age: 32
                    }) {
                      id
                      name
                      email
                      age
                    }
                  }")

  ;; Update a user
  (execute-query schema
                 "mutation {
                    updateUser(input: {
                      id: \"00000000-0000-0000-0000-000000000001\"
                      age: 31
                    }) {
                      id
                      name
                      email
                      age
                    }
                  }")

  ;; Delete a user
  (execute-query schema
                 "mutation {
                    deleteUser(input: {
                      id: \"00000000-0000-0000-0000-000000000002\"
                    })
                  }")

  ;; Print database state
  (db/print-db-state)

  )

