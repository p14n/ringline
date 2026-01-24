(ns ringline.response.transformer-test
  "Contract tests for Datomic to GraphQL response transformation"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.response.transformer :as transformer]
            [ringline.schema.parser :as parser]
            [ringline.fixtures :as fixtures]))

(deftest datomic->graphql-test
  (testing "datomic->graphql transforms field names from namespaced to simple keywords"
    (let [datomic-entity {:user/id "123" :user/email "test@example.com" :user/username "testuser"}
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]
      (is (map? result) "Returns a map")
      (is (contains? result :id) "Has :id field")
      (is (contains? result :email) "Has :email field")
      (is (contains? result :username) "Has :username field")
      (is (= "123" (:id result)) "Preserves field values")
      (is (= "test@example.com" (:email result)) "Preserves field values")))
  
  (testing "datomic->graphql handles nested relationships"
    (let [datomic-entity {:user/id "123" 
                          :user/email "test@example.com"
                          :user/posts [{:post/id "p1" :post/title "First Post"}
                                       {:post/id "p2" :post/title "Second Post"}]}
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]
      (is (contains? result :posts) "Has posts field")
      (is (vector? (:posts result)) "Posts is a vector")
      (is (= 2 (count (:posts result))) "Has correct number of posts")))
  
  (testing "datomic->graphql handles single relationship"
    (let [datomic-entity {:post/id "p1" 
                          :post/title "Test Post"
                          :post/author {:user/id "u1" :user/username "author"}}
          parsed (parser/parse-schema :post fixtures/post-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]
      (is (contains? result :author) "Has author field")
      (is (map? (:author result)) "Author is a map")))
  
  (testing "datomic->graphql handles missing optional fields"
    (let [datomic-entity {:user/id "123" :user/email "test@example.com"}
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]
      (is (map? result) "Returns a map even with missing fields")
      (is (contains? result :id) "Has required fields")
      (is (contains? result :email) "Has required fields")))
  
  (testing "datomic->graphql preserves type coercion"
    (let [datomic-entity {:user/id "123" :user/email "test@example.com"}
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]
      (is (string? (:id result)) "ID is string")
      (is (string? (:email result)) "Email is string"))))

(deftest entities->graphql-test
  (testing "entities->graphql transforms multiple entities"
    (let [datomic-entities [{:user/id "1" :user/email "user1@example.com"}
                            {:user/id "2" :user/email "user2@example.com"}
                            {:user/id "3" :user/email "user3@example.com"}]
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/entities->graphql datomic-entities parsed)]
      (is (vector? result) "Returns a vector")
      (is (= 3 (count result)) "Has correct number of entities")
      (is (every? map? result) "All results are maps")))
  
  (testing "entities->graphql transforms all fields in each entity"
    (let [datomic-entities [{:user/id "1" :user/email "user1@example.com"}
                            {:user/id "2" :user/email "user2@example.com"}]
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/entities->graphql datomic-entities parsed)]
      (is (every? #(contains? % :id) result) "All have :id")
      (is (every? #(contains? % :email) result) "All have :email")))
  
  (testing "entities->graphql handles empty list"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/entities->graphql [] parsed)]
      (is (vector? result) "Returns a vector")
      (is (empty? result) "Empty vector for empty input")))
  
  (testing "entities->graphql handles single entity"
    (let [datomic-entities [{:user/id "1" :user/email "user1@example.com"}]
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/entities->graphql datomic-entities parsed)]
      (is (= 1 (count result)) "Has one entity"))))

(deftest transform-with-selections-test
  (testing "transform-with-selections filters fields based on QueryContext"
    (let [datomic-entity {:user/id "123" :user/email "test@example.com" :user/username "testuser"}
          query-ctx {:entity-type :User
                     :selections [:id :email]
                     :arguments {}
                     :nested-queries {}}
          result (transformer/transform-with-selections datomic-entity query-ctx)]
      (is (map? result) "Returns a map")
      (is (contains? result :id) "Has selected :id field")
      (is (contains? result :email) "Has selected :email field")
      (is (not (contains? result :username)) "Excludes non-selected :username field")))
  
  (testing "transform-with-selections includes nested selections"
    (let [datomic-entity {:user/id "123" 
                          :user/email "test@example.com"
                          :user/posts [{:post/id "p1" :post/title "Post 1"}]}
          query-ctx {:entity-type :User
                     :selections [:id :posts]
                     :arguments {}
                     :nested-queries {:posts {:selections [:id :title]}}}
          result (transformer/transform-with-selections datomic-entity query-ctx)]
      (is (contains? result :id) "Has :id")
      (is (contains? result :posts) "Has :posts")
      (is (vector? (:posts result)) "Posts is a vector")))
  
  (testing "transform-with-selections handles all fields selected"
    (let [datomic-entity {:user/id "123" :user/email "test@example.com" :user/username "testuser"}
          query-ctx {:entity-type :User
                     :selections [:id :email :username]
                     :arguments {}
                     :nested-queries {}}
          result (transformer/transform-with-selections datomic-entity query-ctx)]
      (is (= 3 (count result)) "Has all three fields")))
  
  (testing "transform-with-selections handles empty selections"
    (let [datomic-entity {:user/id "123" :user/email "test@example.com"}
          query-ctx {:entity-type :User
                     :selections []
                     :arguments {}
                     :nested-queries {}}
          result (transformer/transform-with-selections datomic-entity query-ctx)]
      (is (map? result) "Returns a map")
      (is (empty? result) "Empty map for no selections"))))

