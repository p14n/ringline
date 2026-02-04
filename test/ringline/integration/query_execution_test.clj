(ns ringline.integration.query-execution-test
  "Integration tests for end-to-end query conversion"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.query.converter :as converter]
            [ringline.response.transformer :as transformer]
            [ringline.schema.parser :as parser]
            [ringline.fixtures :as fixtures]))

(deftest end-to-end-query-conversion-test
  (testing "Complete workflow: Lacinia context -> QueryContext -> PullPattern"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {} :email {} :username {}}
                        :arguments {}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          pull-pattern (converter/graphql->pull query-ctx)]

      (testing "Query context is built correctly"
        (is (= :User (:entity-type query-ctx)))
        (is (seq (:selections query-ctx)))
        (is (map? (:arguments query-ctx))))

      (testing "Pull pattern is generated correctly"
        (is (vector? (:pattern pull-pattern)))
        (is (seq (:pattern pull-pattern)))
        (is (some #(= :user/id %) (:pattern pull-pattern)))
        (is (some #(= :user/email %) (:pattern pull-pattern)))
        (is (some #(= :user/username %) (:pattern pull-pattern))))))

  (testing "Query with arguments generates where clauses"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {} :email {}}
                        :arguments {:email "test@example.com"}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          result (converter/pull-with-args query-ctx {} {})]

      (is (vector? (:pattern result)) "Has pull pattern")
      (is (vector? (:where-clauses result)) "Has where clauses")
      (is (seq (:where-clauses result)) "Where clauses generated for arguments")))

  (testing "Nested relationship query generates nested pull"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {}
                                     :email {}
                                     :posts {:selections {:id {} :title {} :content {}}}}
                        :arguments {}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          pull-pattern (converter/graphql->pull query-ctx)]

      (testing "Nested query context is captured"
        (is (contains? (:nested-queries query-ctx) :posts))
        (is (seq (get-in query-ctx [:nested-queries :posts :selections]))))

      (testing "Pull pattern includes nested relationship"
        (is (some map? (:pattern pull-pattern)) "Pattern contains nested pull map"))))

  (testing "Multi-level nested relationships"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {}
                                     :posts {:selections {:id {}
                                                          :title {}
                                                          :author {:selections {:id {} :username {}}}}}}
                        :arguments {}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          pull-pattern (converter/graphql->pull query-ctx)]

      (is (vector? (:pattern pull-pattern)) "Generates valid pattern for deep nesting")
      (is (some map? (:pattern pull-pattern)) "Contains nested structures")))

  (testing "Query with multiple arguments"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {} :email {} :username {}}
                        :arguments {:email "test@example.com" :username "testuser"}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          result (converter/pull-with-args query-ctx {} {})]

      (is (>= (count (:where-clauses result)) 2)
          "Generates where clause for each argument")))

  (testing "Query without arguments has no where clauses"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {} :email {}}
                        :arguments {}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          result (converter/pull-with-args query-ctx {} {})]

      (is (empty? (:where-clauses result)) "No where clauses when no arguments")))

  (testing "Complex query with both nesting and arguments"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {}
                                     :email {}
                                     :posts {:selections {:id {} :title {}}}}
                        :arguments {:email "test@example.com"}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)
          result (converter/pull-with-args query-ctx {} {})]
      (is (vector? (:pattern result)) "Has pull pattern")
      (is (seq (:where-clauses result)) "Has where clauses")
      (is (some map? (:pattern result)) "Pattern includes nested pulls"))))

(deftest end-to-end-response-transformation-test
  (testing "Complete workflow: Datomic entity -> GraphQL response"
    (let [datomic-entity {:user/id "123"
                          :user/email "test@example.com"
                          :user/username "testuser"}
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]

      (testing "Entity is transformed correctly"
        (is (map? result))
        (is (= "123" (:id result)))
        (is (= "test@example.com" (:email result)))
        (is (= "testuser" (:username result))))))

  (testing "Transform with selections filters fields"
    (let [datomic-entity {:user/id "123"
                          :user/email "test@example.com"
                          :user/username "testuser"}
          query-ctx {:entity-type :User
                     :selections [:id :email]
                     :arguments {}
                     :nested-queries {}}
          result (transformer/transform-with-selections datomic-entity query-ctx {})]

      (is (contains? result :id) "Has selected field")
      (is (contains? result :email) "Has selected field")
      (is (not (contains? result :username)) "Excludes non-selected field")))

  (testing "Transform multiple entities"
    (let [datomic-entities [{:user/id "1" :user/email "user1@example.com"}
                            {:user/id "2" :user/email "user2@example.com"}]
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/entities->graphql datomic-entities parsed)]

      (is (= 2 (count result)) "Transforms all entities")
      (is (every? #(contains? % :id) result) "All have :id")
      (is (every? #(contains? % :email) result) "All have :email")))

  (testing "Transform with nested relationships"
    (let [datomic-entity {:user/id "123"
                          :user/email "test@example.com"
                          :user/posts [{:post/id "p1" :post/title "Post 1"}
                                       {:post/id "p2" :post/title "Post 2"}]}
          parsed (parser/parse-schema :user fixtures/user-schema)
          result (transformer/datomic->graphql datomic-entity parsed)]

      (is (contains? result :posts) "Has posts relationship")
      (is (vector? (:posts result)) "Posts is a vector")
      (is (= 2 (count (:posts result))) "Has all posts"))))

