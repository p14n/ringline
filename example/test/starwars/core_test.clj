(ns starwars.core-test
  "Integration tests for User CRUD operations with Datomic in-memory database.

   These tests demonstrate the complete workflow:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Automatic CRUD mutations (create, update, delete)
   - Query resolvers with searchable fields
   - Real Datomic in-memory database"
  (:require [clojure.test :refer [deftest testing is]]
            [starwars.core :as core]
            [datomic.api :as d]))

(def test-db-uri "datomic:mem://user-example-test")

(defn cleanup-database!
  "Clean up the test database"
  [conn]
  (d/release conn)
  (d/delete-database test-db-uri))

(deftest example-test
  (let [conn (core/create-database!)
        schema (core/create-schema conn)]
    (try

      (testing "Query user by email returns correct user with all fields"
        (let [result (core/execute-query schema
                                         "{ user(email: \"alice@example.com\") { id name email age } }"
                                         conn)]
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
              "Returns correct age")))

      (testing "Query user by name returns correct user with all fields"
        (let [result (core/execute-query schema
                                         "{ user(name: \"Bob Smith\") { id name email age } }"
                                         conn)]
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
              "Returns correct age")))

      (testing "Query user by name returns correct user with all fields"
        (let [result (core/execute-query schema
                                         "{ user(name: \"Bob Smith\") { id name email age } }"
                                         conn)]
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
              "Returns correct age")))

      (testing "Create mutation creates new user with generated UUID"
        (let [result (core/execute-query schema
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
              "Returns correct age")))

      (testing "Update mutation updates user age and returns updated entity"
        (let [result (core/execute-query schema
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
          (is (nil? (:errors result)) "No errors in response")
          (is (= "00000000-0000-0000-0000-000000000002"
                 (get-in result [:data :updateUser :id]))
              "Returns correct user ID")
          (is (= 26
                 (get-in result [:data :updateUser :age]))
              "Returns updated age")

          ;; Verify the update persisted
          (let [verify-result (core/execute-query schema
                                                  "{ user(email: \"bob@example.com\") { id name email age } }"
                                                  conn)]
            (is (= 26
                   (get-in verify-result [:data :user :age]))
                "Update persisted in database"))))

      (testing "Delete mutation deletes user and returns true"
        (let [result (core/execute-query schema
                                         "mutation {
                                          deleteUser(input: {
                                            id: \"00000000-0000-0000-0000-000000000003\"
                                          })
                                        }"
                                         conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= true
                 (get-in result [:data :deleteUser]))
              "Returns true on successful delete")))
      (finally
        (cleanup-database! conn)))))

