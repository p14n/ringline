(ns starwars.core-test
  "Integration tests for User CRUD operations via HTTP GraphQL server.

   These tests demonstrate the complete workflow:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Automatic CRUD mutations (create, update, delete)
   - Query resolvers with searchable fields
   - Real Datomic in-memory database
   - HTTP server with Ring, Reitit, and Jetty
   - GraphQL over HTTP with JSON"
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [starwars.server :as server]
            [babashka.http-client :as http]
            [babashka.json :as json]))

(def test-port 8765)
(def graphql-url (str "http://localhost:" test-port "/graphql"))

(defn graphql-request
  "Execute a GraphQL query or mutation via HTTP POST"
  [query]
  (let [response (http/post graphql-url
                            {:headers {"Content-Type" "application/json"}
                             :body (json/write-str {:query query})})
        body (json/read-str (:body response) keyword)]
    body))

(defn start-test-server!
  "Start the HTTP server for testing"
  []
  (server/start-server! :port test-port))

(defn stop-test-server!
  "Stop the HTTP server after testing"
  []
  (server/stop-server!))

(defn server-fixture
  "Fixture to start and stop the server around all tests"
  [f]
  (start-test-server!)
  (Thread/sleep 1000) ; Give server time to start
  (try
    (f)
    (finally
      (stop-test-server!))))

(use-fixtures :once server-fixture)

(deftest example-test
  (testing "Query user by email returns correct user with all fields"
    (let [result (graphql-request "{ user(email: \"alice@example.com\") { id name email age } }")]
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
    (let [result (graphql-request "{ user(name: \"Bob Smith\") { id name email age } }")]
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
    (let [result (graphql-request "mutation {
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
    (let [result (graphql-request "mutation {
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
      (is (nil? (:errors result)) "No errors in response")
      (is (= "00000000-0000-0000-0000-000000000002"
             (get-in result [:data :updateUser :id]))
          "Returns correct user ID")
      (is (= 26
             (get-in result [:data :updateUser :age]))
          "Returns updated age")

      ;; Verify the update persisted
      (let [verify-result (graphql-request "{ user(email: \"bob@example.com\") { id name email age } }")]
        (is (= 26
               (get-in verify-result [:data :user :age]))
            "Update persisted in database"))))

  (testing "Delete mutation deletes user and returns true"
    (let [result (graphql-request "mutation {
                                     deleteUser(input: {
                                       id: \"00000000-0000-0000-0000-000000000003\"
                                     })
                                   }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= true
             (get-in result [:data :deleteUser]))
          "Returns true on successful delete"))))

