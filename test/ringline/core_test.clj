(ns ringline.core-test
  "Contract tests for high-level framework API"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.core :as core]
            [ringline.fixtures :as fixtures]))

(deftest init-framework-test
  (testing "init-framework processes multiple schemas"
    (let [schemas [fixtures/user-schema
                   fixtures/post-schema]
          result (core/init-framework schemas {})]
      (is (map? result) "Returns a map")
      (is (contains? result :datomic) "Has :datomic key")
      (is (contains? result :lacinia) "Has :lacinia key")
      (is (contains? result :parsed) "Has :parsed key")))

  (testing "init-framework generates Datomic schemas"
    (let [schemas [fixtures/user-schema
                   fixtures/post-schema]
          result (core/init-framework schemas {})
          datomic (:datomic result)]
      (is (vector? datomic) "Datomic is a vector")
      (is (seq datomic) "Has Datomic schemas")
      (is (every? map? datomic) "All Datomic schemas are maps")))

  (testing "init-framework generates Lacinia schema"
    (let [schemas [fixtures/user-schema
                   fixtures/post-schema]
          result (core/init-framework schemas {})
          lacinia (:lacinia result)]
      (is (map? lacinia) "Lacinia is a map")
      (is (contains? lacinia :objects) "Has :objects")
      (is (contains? lacinia :queries) "Has :queries")))

  (testing "init-framework returns parsed schemas"
    (let [schemas [fixtures/user-schema
                   fixtures/post-schema]
          result (core/init-framework schemas {})
          parsed (:parsed result)]
      (is (vector? parsed) "Parsed is a vector")
      (is (= 2 (count parsed)) "Has all parsed schemas")))

  (testing "init-framework handles single schema"
    (let [schemas [fixtures/user-schema]
          result (core/init-framework schemas {})]
      (is (= 1 (count (:parsed result))) "Has one parsed schema")
      (is (seq (:datomic result)) "Has Datomic schema")
      (is (map? (:lacinia result)) "Has Lacinia schema")))

  (testing "init-framework handles empty schemas map"
    (let [result (core/init-framework {} {})]
      (is (map? result) "Returns a map")
      (is (empty? (:parsed result)) "No parsed schemas")
      (is (empty? (:datomic result)) "No Datomic schemas")))

  (testing "init-framework preserves relationships across schemas"
    (let [schemas fixtures/test-schemas
          result (core/init-framework schemas {})
          lacinia (:lacinia result)]
      (is (contains? (:objects lacinia) :User) "Has User object")
      (is (contains? (:objects lacinia) :Post) "Has Post object")
      (is (contains? (:objects lacinia) :Comment) "Has Comment object"))))

(deftest create-resolver-test
  (testing "create-resolver returns a function"
    (let [schemas [fixtures/user-schema]
          framework (core/init-framework schemas {})
          mock-conn {:db-after nil}  ; Mock Datomic connection
          resolver (core/create-resolver :User)]
      (is (fn? resolver) "Returns a function")))

  (testing "create-resolver function accepts Lacinia context and args"
    (let [schemas [fixtures/user-schema]
          framework (core/init-framework schemas {})
          mock-conn {:db-after nil}
          resolver (core/create-resolver :User)]
      ;; Resolver should be callable with (context, args, value)
      (is (fn? resolver) "Resolver is a function")))

  (testing "create-resolver handles different entity types"
    (let [schemas [fixtures/user-schema fixtures/post-schema]
          framework (core/init-framework schemas {})
          mock-conn {:db-after nil}
          user-resolver (core/create-resolver :User)
          post-resolver (core/create-resolver :Post)]
      (is (fn? user-resolver) "User resolver is a function")
      (is (fn? post-resolver) "Post resolver is a function")
      (is (not= user-resolver post-resolver) "Different resolvers for different entities")))

  (testing "create-resolver handles nil connection gracefully"
    (let [schemas [fixtures/user-schema]
          framework (core/init-framework schemas {})
          resolver (core/create-resolver :User)]
      (is (fn? resolver) "Returns a function even with nil connection"))))

