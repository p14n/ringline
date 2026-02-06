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
  (:require
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [spyscope.core]
   [starwars.auto :as auto]
   [starwars.custom :as custom]
   [starwars.server :as server]))

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

(deftest auto-test
  (let [db-uri auto/db-uri
        server-state (server/start-server! auto/create-graphql-system! :db-uri db-uri :port test-port)]
    (try
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
                   (get-in result2 [:data :updateHuman :home_planet :name]))
                "Returns updated home planet")))

        (testing "Delete human"
          (let [result3 (graphql-request (format "mutation {
                            deleteHuman(input:  {
                              id: \"%s\"
                            })}" id))]
            (is (nil? (:errors result3)) "No errors in response")
            (is (= true (get-in result3 [:data :deleteHuman]))
                "Returns delete result")))

        (testing "Query for human just deleted"
          (let [result4 (graphql-request (format "{ human(id: \"%s\") { id name home_planet { name } } }" id))]
            (is (nil? (:errors result4)) "No errors in response")
            (is (nil? (get-in result4 [:data :human :id])) "Returns no ID"))))

      (finally
        (server/stop-server! server-state)))))

(deftest custom-test
  (let [db-uri custom/db-uri
        server-state (server/start-server! custom/create-custom-graphql-system! :db-uri db-uri :port test-port)]
    (try

      (testing "Adding an org returns new org with new id"
        (let [result (graphql-request "mutation {
                    createOrganization(input:  {
                      name: \"Dean LLC\", country_of_incorporation_code: \"UK\"
                    }) { id name country_of_incorporation_code }}")]
          (is (nil? (:errors result)) "No errors in response")
          (is (not (nil?
                    (get-in result [:data :createOrganization :id])))
              "Returns new ID")
          (is (= "Dean LLC"
                 (get-in result [:data :createOrganization :name]))
              "Returns new organization name")))

      (let [result (graphql-request "{ organization(name: \"Dean LLC\") { id name country_of_incorporation_code } }")
            id (get-in result [:data :organization :id])]

        (testing "Query for org just created"
          (is (nil? (:errors result)) "No errors in response")
          (is (not (nil? id))
              "Returns ID")
          (is (= "Dean LLC"
                 (get-in result [:data :organization :name]))
              "Returns org's name")
          (is (= "UK"
                 (get-in result [:data :organization :country_of_incorporation_code]))
              "Returns Dean's home planet"))

        (testing "Update org"
          (let [result2 (graphql-request (format "mutation {
                        updateOrganization(input:  {
                          id: \"%s\", name: \"Dean PLC\", country_of_incorporation_code: \"US\"
                        }) { id name country_of_incorporation_code }}" id))
                data (-> result2 :data :updateOrganization)]
            (is (nil? (:errors result2)) "No errors in response")
            (is (= "Dean PLC" (:name data)) "Returns updated name")
            (is (= "US" (:country_of_incorporation_code data)) "Returns updated code")))

        (testing "Create party"
          (let [result3 (graphql-request "mutation {
                            createPii(input:  {
                              email: \"test1@example.com\"
                            }) { id } }")
                piiId (get-in result3 [:data :createPii :id])
                result4 (graphql-request (format "mutation {
                            createParty(input:  {
                              personal_info: \"%s\", organization: \"%s\"
                            }) { personal_info { email } organization { name } }}" piiId id))]
            (is (nil? (:errors result3)) "No errors in response")
            (is (not (nil? piiId)) "Returns ID")
            (is (nil? (:errors result4)) "No errors in response")
            (is (= {:organization {:name "Dean PLC"}, :personal_info {:email "test1@example.com"}}
                   (get-in result4 [:data :createParty])) "Returns party")))

        (testing "Invite party with custom mutation"
          (let [result5 (graphql-request
                         (format "mutation { 
                           inviteParty(email: \"test2@example.com\", organization: \"%s\") { 
                            personal_info { email } organization { id }
                          } }" id))]
            (is (nil? (:errors result5)) "No errors in response")
            (is (= {:organization {:id id}, :personal_info {:email "test2@example.com"}}
                   (get-in result5 [:data :inviteParty])) "Returns party")))

        (testing "All parties in organization"
          (let [result6 (graphql-request
                         (format "{ allParties(organization: \"%s\") { id personal_info { email } organization { name } }}" id))
                data (->> result6 :data :allParties (sort-by (comp :email :personal_info)))]
            (is (nil? (:errors result6)) "No errors in response")
            (is (= 2 (count data)) "Returns two parties")
            (is (= {:organization {:name "Dean PLC"}, :personal_info {:email "test1@example.com"}}
                   (dissoc (first data) :id)) "Returns first party")
            (is (= {:organization {:name "Dean PLC"}, :personal_info {:email "test2@example.com"}}
                   (dissoc (second data) :id)) "Returns second party")

            (testing "Create address and party address"
              (let [result7 (graphql-request
                             (format "mutation { 
                                               createAddress(input: {
                                                street: \"123 Main St\", city: \"Anytown\", state: \"CA\", postal_code: \"12345\", country_code: \"US\"
                                               }) { 
                                                id street city state postal_code country_code
                                              } }"))
                    pid (-> data first :id)
                    address-id (get-in result7 [:data :createAddress :id])
                    result8 (graphql-request
                             (format "mutation { createParty_address(input: {
                                                                party:\"%s\", address:\"%s\"
                                                               }) { 
                                                                id
                                                              } }" pid address-id))
                    result9 (graphql-request
                             (format "query { party(id:\"%s\" ) { addresses { address { city postal_code}} } }" pid))
                    data9 (get-in result9 [:data :party])]
                (is (nil? (:errors result7)) "No errors in response")
                (is (nil? (:errors result8)) "No errors in response")
                (is (=  {:addresses [{:address {:city "Anytown", :postal_code "12345"}}]}
                        data9)))))))

      (finally
        (server/stop-server! server-state)))))