(ns starwars.core
  "User CRUD example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Automatic CRUD mutations (create, update, delete)
   - Query resolvers with searchable fields
   - Datomic in-memory database"
  (:require [ringline.core :as ringline]
            [ringline.schema.datomic :as ringline-datomic]
            [starwars.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [datomic.api :as d]
            [clojure.pprint :as pprint]))

;; Datomic database setup
(def db-uri "datomic:mem://user-example")

(defn create-database!
  "Create and initialize Datomic in-memory database with schema"
  []
  (println "\n=== Creating Datomic In-Memory Database ===")

  ;; Create the database
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")

    ;; Initialize framework to get Datomic schema
    (println "\n=== Generating Datomic Schema from Malli ===")
    (let [framework (ringline/init-framework schema/schemas {})
          datomic-schemas (:datomic framework)

          ;; Convert to transaction data
          tx-data (mapcat ringline-datomic/schema->transaction datomic-schemas)]

      (println "Generated Datomic attributes:")
      (doseq [attr tx-data]
        (println "  " (:db/ident attr) "->" (:db/valueType attr) (:db/cardinality attr)))

      ;; Transact schema
      (println "\n=== Installing Datomic Schema ===")
      @(d/transact conn tx-data)
      (println "Schema installed successfully")

      ;; Add initial data
      (println "\n=== Adding Initial User Data ===")
      (let [initial-users [{:db/id (d/tempid :db.part/user)
                           :user/id #uuid "00000000-0000-0000-0000-000000000001"
                           :user/name "Alice Johnson"
                           :user/email "alice@example.com"
                           :user/age 30}
                          {:db/id (d/tempid :db.part/user)
                           :user/id #uuid "00000000-0000-0000-0000-000000000002"
                           :user/name "Bob Smith"
                           :user/email "bob@example.com"
                           :user/age 25}
                          {:db/id (d/tempid :db.part/user)
                           :user/id #uuid "00000000-0000-0000-0000-000000000003"
                           :user/name "Charlie Brown"
                           :user/email "charlie@example.com"
                           :user/age 35}]]
        @(d/transact conn initial-users)
        (println "Added 3 sample users"))

      conn)))

;; Create the complete GraphQL schema with resolvers
(defn create-schema
  "Create the complete Lacinia schema with resolvers attached and compiled"
  [conn]
  (let [framework (ringline/init-framework schema/schemas {})
        lacinia-schema (:lacinia framework)
        parsed-schemas (:parsed framework)

        ;; Attach mutation resolvers
        schema-with-mutations (ringline/attach-mutation-resolvers
                               lacinia-schema
                               schema/schemas
                               conn)

        ;; Attach automatic query resolver using ringline/create-resolver
        user-resolver (ringline/create-resolver :user conn (get parsed-schemas :user))
        schema-with-all-resolvers (assoc-in schema-with-mutations
                                            [:queries :user :resolve]
                                            user-resolver)]
    ;; Compile the schema
    (lacinia-schema/compile schema-with-all-resolvers)))

;; Execute a GraphQL query
(defn execute-query
  "Execute a GraphQL query against the schema with Datomic connection in context"
  [schema query-string conn]
  (lacinia/execute schema query-string nil {:conn conn}))

;; Example queries
(defn example-queries
  "Run example queries to demonstrate the system"
  []
  ;; Create and initialize Datomic database
  (let [conn (create-database!)
        schema (create-schema conn)]
    (println "\n=== Creating GraphQL Schema ===")
    (println "Schema created successfully")

    (println "\n=== Example Query: Search User by Email ===")
    (let [result (execute-query schema
                               "{ user(email: \"alice@example.com\") { id name email age } }"
                               conn)]
      (pprint/pprint result))

    (println "\n=== Example Query: Search User by Name ===")
    (let [result (execute-query schema
                               "{ user(name: \"Bob Smith\") { id name email age } }"
                               conn)]
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
                                }"
                               conn)]
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
                                }"
                               conn)]
      (pprint/pprint result))

    (println "\n=== Verify Update: Query Updated User ===")
    (let [result (execute-query schema
                               "{ user(email: \"bob@example.com\") { id name email age } }"
                               conn)]
      (pprint/pprint result))

    (println "\n=== Example Mutation: Delete User ===")
    (let [result (execute-query schema
                               "mutation {
                                  deleteUser(input: {
                                    id: \"00000000-0000-0000-0000-000000000003\"
                                  })
                                }"
                               conn)]
      (pprint/pprint result))

    (println "\n=== All Examples Complete ===\n")

    ;; Clean up
    (d/release conn)
    (d/delete-database db-uri)))

(defn -main
  "Main entry point for the example"
  [& args]
  (example-queries))

(comment
  ;; REPL examples

  ;; Create database and schema
  (def conn (create-database!))
  (def schema (create-schema conn))

  ;; Query for a user by email
  (execute-query schema
                 "{ user(email: \"alice@example.com\") { id name email age } }"
                 conn)

  ;; Query for a user by name
  (execute-query schema
                 "{ user(name: \"Bob Smith\") { id name email age } }"
                 conn)

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
                  }"
                 conn)

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
                  }"
                 conn)

  ;; Delete a user
  (execute-query schema
                 "mutation {
                    deleteUser(input: {
                      id: \"00000000-0000-0000-0000-000000000002\"
                    })
                  }"
                 conn)

  ;; Query the database directly with Datalog
  (d/q '[:find ?name ?email
         :where
         [?e :user/name ?name]
         [?e :user/email ?email]]
       (d/db conn))

  ;; Clean up
  (d/release conn)
  (d/delete-database db-uri)

  )

