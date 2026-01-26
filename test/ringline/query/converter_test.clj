(ns ringline.query.converter-test
  "Contract tests for GraphQL to Datomic pull pattern conversion"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.query.converter :as converter]))

(deftest build-query-context-test
  (testing "build-query-context extracts selections from Lacinia context"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection {:selections {:id {} :email {} :username {}}}}
          result (converter/build-query-context lacinia-ctx :User)]
      (is (map? result) "Returns a map")
      (is (= :User (:entity-type result)) "Has correct entity type")
      (is (vector? (:selections result)) "Selections is a vector")
      (is (seq (:selections result)) "Has selections")))

  (testing "build-query-context extracts arguments"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection {:arguments {:email "test@example.com"}}}
          result (converter/build-query-context lacinia-ctx :User)]
      (is (map? (:arguments result)) "Arguments is a map")
      (is (= "test@example.com" (get-in result [:arguments :email])) "Extracts argument values")))

  (testing "build-query-context handles nested selections"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {}
                                     :posts {:selections {:id {} :title {}}}}}}
          result (converter/build-query-context lacinia-ctx :User)]
      (is (map? (:nested-queries result)) "Has nested queries")
      (is (contains? (:nested-queries result) :posts) "Identifies nested relationship")))

  (testing "build-query-context handles simple selections without nesting"
    (let [lacinia-ctx {:com.walmartlabs.lacinia/selection {:selections {:id {} :email {}}}}
          result (converter/build-query-context lacinia-ctx :User)]
      (is (contains? (set (:selections result)) :id) "Has id selection")
      (is (contains? (set (:selections result)) :email) "Has email selection"))))

(deftest graphql->pull-test
  (testing "graphql->pull converts simple selections to pull pattern"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email :username]
                     :arguments {}
                     :nested-queries {}}
          result (converter/graphql->pull query-ctx)]
      (is (map? result) "Returns a map")
      (is (vector? (:pattern result)) "Pattern is a vector")
      (is (seq (:pattern result)) "Pattern is non-empty")))

  (testing "graphql->pull includes all requested fields in pattern"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email :username]
                     :arguments {}
                     :nested-queries {}}
          result (converter/graphql->pull query-ctx)
          pattern (:pattern result)]
      (is (some #(= :user/id %) pattern) "Pattern includes id")
      (is (some #(= :user/email %) pattern) "Pattern includes email")
      (is (some #(= :user/username %) pattern) "Pattern includes username")))

  (testing "graphql->pull handles nested relationships"
    (let [query-ctx {:entity-type :User
                     :selections [:id :posts]
                     :arguments {}
                     :nested-queries {:posts {:selections [:id :title]}}}
          result (converter/graphql->pull query-ctx)
          pattern (:pattern result)]
      (is (some map? pattern) "Pattern contains nested pull for relationship")))

  (testing "graphql->pull handles multiple levels of nesting"
    (let [query-ctx {:entity-type :User
                     :selections [:id :posts]
                     :arguments {}
                     :nested-queries {:posts {:selections [:id :author]
                                              :nested-queries {:author {:selections [:id :username]}}}}}
          result (converter/graphql->pull query-ctx)]
      (is (vector? (:pattern result)) "Returns valid pattern with deep nesting")))

  (testing "graphql->pull handles empty selections"
    (let [query-ctx {:entity-type :User
                     :selections []
                     :arguments {}
                     :nested-queries {}}
          result (converter/graphql->pull query-ctx)]
      (is (vector? (:pattern result)) "Returns a pattern even with no selections"))))

(deftest pull-with-args-test
  (testing "pull-with-args generates pattern with where clauses"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email]
                     :arguments {:email "test@example.com"}
                     :nested-queries {}}
          result (converter/pull-with-args query-ctx {})]
      (is (map? result) "Returns a map")
      (is (vector? (:pattern result)) "Has pattern")
      (is (vector? (:where-clauses result)) "Has where clauses")))

  (testing "pull-with-args creates where clause for each argument"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email]
                     :arguments {:email "test@example.com"}
                     :nested-queries {}}
          result (converter/pull-with-args query-ctx {})
          where-clauses (:where-clauses result)]
      (is (seq where-clauses) "Has at least one where clause")
      (is (every? vector? where-clauses) "All where clauses are vectors")))

  (testing "pull-with-args handles multiple arguments"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email :username]
                     :arguments {:email "test@example.com" :username "testuser"}
                     :nested-queries {}}
          result (converter/pull-with-args query-ctx {})
          where-clauses (:where-clauses result)]
      (is (>= (count where-clauses) 2) "Has where clause for each argument")))

  (testing "pull-with-args handles no arguments"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email]
                     :arguments {}
                     :nested-queries {}}
          result (converter/pull-with-args query-ctx {})]
      (is (empty? (:where-clauses result)) "No where clauses when no arguments")))

  (testing "pull-with-args preserves pull pattern"
    (let [query-ctx {:entity-type :User
                     :selections [:id :email]
                     :arguments {:email "test@example.com"}
                     :nested-queries {}}
          result (converter/pull-with-args query-ctx {})
          pattern (:pattern result)]
      (is (some #(= :user/id %) pattern) "Pattern includes requested fields")
      (is (some #(= :user/email %) pattern) "Pattern includes requested fields"))))

