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

  (testing "Query for human by ID returns Luke Skywalker with all fields"
    (let [result (graphql-request "{ human(id: \"1000\") { id name home_planet { name } } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= "1000"
             (get-in result [:data :human :id]))
          "Returns Luke's ID")
      (is (= "Luke Skywalker"
             (get-in result [:data :human :name]))
          "Returns Luke's name")
      (is (= "Tatooine"
             (get-in result [:data :human :home_planet :name]))
          "Returns Luke's home planet")))

  (testing "Query for droid by ID returns R2-D2 with all fields"
    (let [result (graphql-request "{ droid(id: \"2000\") { id name primary_function } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (= "2000"
             (get-in result [:data :droid :id]))
          "Returns R2-D2's ID")
      (is (= "R2-D2"
             (get-in result [:data :droid :name]))
          "Returns R2-D2's name")
      (is (= "Astromech"
             (get-in result [:data :droid :primary_function]))
          "Returns R2-D2's primary function"))))

