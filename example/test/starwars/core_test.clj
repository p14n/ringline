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
            [babashka.json :as json]
            [spyscope.core]))

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
  (server/stop-server!)
  (server/shutdown-database!))

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
    (let [result (graphql-request "{ human(name: \"Luke Skywalker\") { id name home_planet { name } } }")]
      (is (nil? (:errors result)) "No errors in response")
      (is (not (nil? (get-in result [:data :human :id])))
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
          "Returns R2-D2's primary function")))

  (testing "Adding a human returns new human with new id"
    (let [result (graphql-request "mutation {
                    createHuman(input:  {
                      name: \"Dean\", home_planet: \"ALDE\"
                    }) { id home_planet { name } }}")]
      (is (nil? (:errors result)) "No errors in response")
      (is (not (nil?
                (get-in result [:data :createHuman :id])))
          "Returns new ID")
      (is (= "Alderaan"
             (get-in result [:data :createHuman :home_planet :name]))
          "Returns new home planet")))

  (let [result (graphql-request "{ human(name: \"Dean\") { id name home_planet { name } } }")
        id (get-in result [:data :human :id])]
    (testing "Query for human just created"
      (is (nil? (:errors result)) "No errors in response")
      (is (not (nil? id))
          "Returns ID")
      (is (= "Dean"
             (get-in result [:data :human :name]))
          "Returns Dean's name")
      (is (= "Alderaan"
             (get-in result [:data :human :home_planet :name]))
          "Returns Dean's home planet"))
    (testing "Update human"
      (let [result2 (graphql-request (format "mutation {
                    updateHuman(input:  {
                      id: \"%s\", name: \"Dean2\", home_planet: \"TTOO\"
                    }) { id name home_planet { name } } }" id))]
        (is (nil? (:errors result2)) "No errors in response")
        (is (= "Dean2"
               (get-in result2 [:data :updateHuman :name]))
            "Returns updated name")
        (is (= "Tatooine"
               (get-in result [:data :human :home_planet :name]))
            "Returns Dean's home planet")))))

