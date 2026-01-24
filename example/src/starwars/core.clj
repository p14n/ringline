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
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")
    (let [framework (ringline/init-framework schema/schemas {})
          datomic-schemas (:datomic framework)
          tx-data (mapcat ringline-datomic/schema->transaction datomic-schemas)]

      @(d/transact conn tx-data)

      ;; Add initial data
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
        @(d/transact conn initial-users))

      conn)))

(defn cleanup-database!
  "Clean up the database connection"
  [conn]
  (d/release conn)
  (d/delete-database db-uri))

;; Create the complete GraphQL schema with resolvers
(defn create-schema
  "Create the complete Lacinia schema with resolvers attached and compiled"
  [conn]
  (let [framework (ringline/init-framework schema/schemas {})
        lacinia-schema (:lacinia framework)

        ;; Attach mutation resolvers
        schema-with-mutations (ringline/attach-mutation-resolvers
                               lacinia-schema
                               schema/schemas
                               conn)

        ;; Attach automatic query resolver using ringline/create-resolver
        user-resolver (ringline/create-resolver :user conn)
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

(defn -main
  "Main entry point for the example"
  [& args]
  (println "\n=== Ringline User CRUD Example ===\n")
  (println "This example demonstrates the Ringline framework with Datomic.")
  (println "")
  (println "To see the framework in action:")
  (println "")
  (println "1. Run the HTTP GraphQL server:")
  (println "   clojure -M:example")
  (println "   Then visit http://localhost:3000/graphiql in your browser")
  (println "")
  (println "2. Run the tests:")
  (println "   # Run example tests only:")
  (println "   clojure -M:test --focus :example")
  (println "")
  (println "   # Run all framework tests (including example):")
  (println "   clojure -M:test")
  (println "")
  (println "3. Explore the code in the REPL using the examples in the comment block below.")
  (println ""))

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
  (d/delete-database db-uri))

