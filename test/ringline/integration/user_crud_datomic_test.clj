(ns ringline.integration.user-crud-datomic-test
  "Integration tests for User CRUD operations with Datomic in-memory database.

   These tests demonstrate the complete workflow:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Automatic CRUD mutations (create, update, delete)
   - Query resolvers with searchable fields
   - Real Datomic in-memory database"
  (:require [clojure.test :refer [deftest testing is]]
            [ringline.core :as ringline]
            [ringline.schema.datomic :as ringline-datomic]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [datomic.api :as d]))

;; User schema with CRUD mutations
(def user-schema
  [:map
   {:ringline/schema-name :user
    :ringline/datomic-ns "user"
    :ringline/query-root true
    :ringline/searchable [:email :name]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:email :string]
   [:age :int]])

(def schemas [user-schema])

(def db-uri "datomic:mem://user-crud-test")

(defn create-test-database!
  "Create and initialize Datomic database with schema and sample data"
  []
  ;; Create database
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]

    ;; Generate and install Datomic schema
    (let [framework (ringline/init-framework schemas {})
          datomic-schemas (:datomic framework)
          tx-data (mapcat ringline-datomic/schema->transaction datomic-schemas)]
      @(d/transact conn tx-data))

    ;; Add sample users
    (let [sample-users [{:db/id (d/tempid :db.part/user)
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
      @(d/transact conn sample-users))

    conn))

(defn create-test-schema
  "Create the complete Lacinia schema with resolvers attached and compiled"
  [conn]
  (let [framework (ringline/init-framework schemas {})
        lacinia-schema (:lacinia framework)
        namespace-lookup (:namespace-lookup framework)
        ;; Attach mutation resolvers
        schema-with-mutations (ringline/attach-mutation-resolvers
                               lacinia-schema
                               (ringline/schemas->schemas-map schemas)
                               conn
                               namespace-lookup
                               {} {})

        ;; Attach automatic query resolver
        user-resolver (ringline/create-resolver :user)
        schema-with-all-resolvers (assoc-in schema-with-mutations
                                            [:queries :user :resolve]
                                            user-resolver)]
    ;; Compile the schema
    [framework (lacinia-schema/compile schema-with-all-resolvers)]))

(defn execute-query
  "Execute a GraphQL query against the schema"
  [schema query-str conn framework]
  (lacinia/execute schema query-str nil (ringline/augment-context {} framework conn)))

(defn cleanup-database!
  "Clean up the test database"
  [conn]
  (d/release conn)
  (d/delete-database db-uri))

(deftest query-user-by-email-test
  (testing "Query user by email returns correct user with all fields"
    (let [conn (create-test-database!)
          [framework schema] (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "{ user(email: \"alice@example.com\") { id name email age } }"
                                    conn framework)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "00000000-0000-0000-0000-000000000001"
                 (get-in result [:data :user :id]))
              "Returns correct user ID")
          (is (= "Alice Johnson"
                 (get-in result [:data :user :name]))
              "Returns correct name")
          (is (= "alice@example.com"
                 (get-in result [:data :user :email]))
              "Returns correct email")
          (is (= 30
                 (get-in result [:data :user :age]))
              "Returns correct age"))
        (finally
          (cleanup-database! conn))))))

(deftest query-user-by-name-test
  (testing "Query user by name returns correct user with all fields"
    (let [conn (create-test-database!)
          [framework schema] (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "{ user(name: \"Bob Smith\") { id name email age } }"
                                    conn framework)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "00000000-0000-0000-0000-000000000002"
                 (get-in result [:data :user :id]))
              "Returns correct user ID")
          (is (= "Bob Smith"
                 (get-in result [:data :user :name]))
              "Returns correct name")
          (is (= "bob@example.com"
                 (get-in result [:data :user :email]))
              "Returns correct email")
          (is (= 25
                 (get-in result [:data :user :age]))
              "Returns correct age"))
        (finally
          (cleanup-database! conn))))))

(deftest create-user-mutation-test
  (testing "Create mutation creates new user with generated UUID"
    (let [conn (create-test-database!)
          [framework schema] (create-test-schema conn)]
      (try
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
                                    conn framework)]
          (is (nil? (:errors result)) "No errors in response")
          (is (some? (get-in result [:data :createUser :id]))
              "Returns generated UUID")
          (is (= "Diana Prince"
                 (get-in result [:data :createUser :name]))
              "Returns correct name")
          (is (= "diana@example.com"
                 (get-in result [:data :createUser :email]))
              "Returns correct email")
          (is (= 28
                 (get-in result [:data :createUser :age]))
              "Returns correct age"))
        (finally
          (cleanup-database! conn))))))

(deftest update-user-mutation-test
  (testing "Update mutation updates user age and returns updated entity"
    (let [conn (create-test-database!)
          [framework schema] (create-test-schema conn)]
      (try
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
                                    conn framework)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "00000000-0000-0000-0000-000000000002"
                 (get-in result [:data :updateUser :id]))
              "Returns correct user ID")
          (is (= 26
                 (get-in result [:data :updateUser :age]))
              "Returns updated age")

          ;; Verify the update persisted
          (let [verify-result (execute-query schema
                                             "{ user(email: \"bob@example.com\") { id name email age } }"
                                             conn framework)]
            (is (= 26
                   (get-in verify-result [:data :user :age]))
                "Update persisted in database")))
        (finally
          (cleanup-database! conn))))))

(deftest delete-user-mutation-test
  (testing "Delete mutation deletes user and returns true"
    (let [conn (create-test-database!)
          [framework schema] (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "mutation {
                                      deleteUser(input: {
                                        id: \"00000000-0000-0000-0000-000000000003\"
                                      })
                                    }"
                                    conn framework)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= true
                 (get-in result [:data :deleteUser]))
              "Returns true on successful delete"))
        (finally
          (cleanup-database! conn))))))

