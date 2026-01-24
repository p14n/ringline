(ns starwars.core-test
  "Integration tests for Star Wars queries via HTTP GraphQL server.

   These tests demonstrate the complete workflow:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Enum support (Episode)
   - Multiple entity types (Human, Droid)
   - Custom query resolvers
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
  (testing "Query for human with default ID returns Darth Vader"
    (let [result (graphql-request "{ human { id name home_planet } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= "1001"
             (get-in result [:data :human :id]))
          "Returns Vader's ID")
      (is (= "Darth Vader"
             (get-in result [:data :human :name]))
          "Returns Vader's name")
      (is (= "Tatooine"
             (get-in result [:data :human :home_planet]))
          "Returns Vader's home planet")))

  (testing "Query for human by ID returns Luke Skywalker with all fields"
    (let [result (graphql-request "{ human(id: \"1000\") { id name home_planet appears_in } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= "1000"
             (get-in result [:data :human :id]))
          "Returns Luke's ID")
      (is (= "Luke Skywalker"
             (get-in result [:data :human :name]))
          "Returns Luke's name")
      (is (= "Tatooine"
             (get-in result [:data :human :home_planet]))
          "Returns Luke's home planet")
      (is (= ["EMPIRE" "JEDI" "NEWHOPE"]
             (sort (get-in result [:data :human :appears_in])))
          "Returns episodes Luke appears in (sorted)")))

  (testing "Query for droid with default ID returns C-3PO"
    (let [result (graphql-request "{ droid { id name primary_function } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= "2001"
             (get-in result [:data :droid :id]))
          "Returns C-3PO's ID")
      (is (= "C-3PO"
             (get-in result [:data :droid :name]))
          "Returns C-3PO's name")
      (is (= "Protocol"
             (get-in result [:data :droid :primary_function]))
          "Returns C-3PO's primary function")))

  (testing "Query for droid by ID returns R2-D2 with all fields"
    (let [result (graphql-request "{ droid(id: \"2000\") { id name primary_function appears_in } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= "2000"
             (get-in result [:data :droid :id]))
          "Returns R2-D2's ID")
      (is (= "R2-D2"
             (get-in result [:data :droid :name]))
          "Returns R2-D2's name")
      (is (= "Astromech"
             (get-in result [:data :droid :primary_function]))
          "Returns R2-D2's primary function")
      (is (= ["EMPIRE" "JEDI" "NEWHOPE"]
             (sort (get-in result [:data :droid :appears_in])))
          "Returns episodes R2-D2 appears in (sorted)"))))

