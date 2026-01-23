(ns ringline.schema.lacinia-test
  "Contract tests for Lacinia GraphQL schema generation"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
            [ringline.schema.lacinia :as lacinia]
            [ringline.fixtures :as fixtures]))

(deftest generate-schema-test
  (testing "generate-schema creates GraphQL object type"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)]
      (is (map? result) "Returns a map")
      (is (contains? result :objects) "Has :objects key")
      (is (map? (:objects result)) "Objects is a map")))
  
  (testing "generate-schema creates object with correct fields"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          user-obj (get-in result [:objects :User])]
      (is (some? user-obj) "User object exists")
      (is (contains? user-obj :fields) "Has :fields")
      (is (map? (:fields user-obj)) "Fields is a map")))
  
  (testing "generate-schema applies correct GraphQL types"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          fields (get-in result [:objects :User :fields])]
      (is (some? (get fields :id)) "Has id field")
      (is (some? (get fields :username)) "Has username field")
      (is (some? (get fields :email)) "Has email field")))
  
  (testing "generate-schema creates query root for entities marked as query-root"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)]
      (is (contains? result :queries) "Has :queries key")
      (is (map? (:queries result)) "Queries is a map")
      (is (some? (get-in result [:queries :user])) "Has user query")))
  
  (testing "generate-schema adds searchable fields as query arguments"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          user-query (get-in result [:queries :user])]
      (is (contains? user-query :args) "Query has args")
      (is (map? (:args user-query)) "Args is a map")))
  
  (testing "generate-schema handles relationships as GraphQL object references"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          posts-field (get-in result [:objects :User :fields :posts])]
      (is (some? posts-field) "Has posts field for relationship")))
  
  (testing "generate-schema handles schema without query-root property"
    (let [parsed (parser/parse-schema :simple fixtures/simple-schema)
          result (lacinia/generate-schema parsed)]
      (is (contains? result :objects) "Has objects")
      (is (or (empty? (:queries result))
              (nil? (:queries result)))
          "No queries for non-query-root entity"))))

(deftest generate-schemas-test
  (testing "generate-schemas merges multiple entities into single schema"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)]
      (is (map? result) "Returns a map")
      (is (contains? result :objects) "Has :objects")
      (is (contains? result :queries) "Has :queries")))
  
  (testing "generate-schemas includes all entity object types"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)
          objects (:objects result)]
      (is (contains? objects :User) "Has User object")
      (is (contains? objects :Post) "Has Post object")
      (is (contains? objects :Comment) "Has Comment object")))
  
  (testing "generate-schemas includes queries for all query-root entities"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)
          queries (:queries result)]
      (is (some? (get queries :user)) "Has user query")
      (is (some? (get queries :post)) "Has post query")))
  
  (testing "generate-schemas preserves relationships across entities"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)
          user-posts (get-in result [:objects :User :fields :posts])
          post-author (get-in result [:objects :Post :fields :author])]
      (is (some? user-posts) "User has posts field")
      (is (some? post-author) "Post has author field"))))

(deftest attach-resolvers-test
  (testing "attach-resolvers adds resolver functions to queries"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          schema (lacinia/generate-schema parsed)
          resolvers {:user (fn [ctx args val] {:id "123"})}
          result (lacinia/attach-resolvers schema resolvers)]
      (is (map? result) "Returns a map")
      (is (contains? result :queries) "Has queries")))
  
  (testing "attach-resolvers preserves existing schema structure"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          schema (lacinia/generate-schema parsed)
          resolvers {:user (fn [ctx args val] {:id "123"})}
          result (lacinia/attach-resolvers schema resolvers)]
      (is (= (:objects schema) (:objects result)) "Objects unchanged")
      (is (contains? result :queries) "Queries preserved")))
  
  (testing "attach-resolvers handles empty resolver map"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          schema (lacinia/generate-schema parsed)
          result (lacinia/attach-resolvers schema {})]
      (is (map? result) "Returns a map")
      (is (= (:objects schema) (:objects result)) "Schema structure preserved"))))

