(ns ringline.mutation.parser-test
  "Contract tests for mutation parser"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.mutation.parser :as parser]
            [ringline.fixtures :as fixtures]))

;; T005: Test parsing schema with all mutation types
(deftest parse-mutations-all-types-test
  (testing "parse-mutations extracts all mutation operations"
    (let [result (parser/parse-mutations :user fixtures/user-with-mutations-schema)]
      (is (map? result) "Returns a map")
      (is (= :user (:entity-type result)) "Has correct entity type")
      (is (= #{:create :update :delete} (:operations result)) "Has all operations")
      (is (some? (:create-schema result)) "Has create schema")
      (is (some? (:update-schema result)) "Has update schema")
      (is (some? (:delete-schema result)) "Has delete schema"))))

;; T006: Test parsing schema with subset of mutations
(deftest parse-mutations-subset-test
  (testing "parse-mutations handles subset of operations"
    (let [result (parser/parse-mutations :post fixtures/post-with-partial-mutations-schema)]
      (is (= :post (:entity-type result)) "Has correct entity type")
      (is (= #{:create :update} (:operations result)) "Has only create and update")
      (is (some? (:create-schema result)) "Has create schema")
      (is (some? (:update-schema result)) "Has update schema")
      (is (nil? (:delete-schema result)) "No delete schema"))))

;; T007: Test parsing schema with no mutations property
(deftest parse-mutations-no-property-test
  (testing "parse-mutations returns empty operations for schema without mutations"
    (let [result (parser/parse-mutations :audit-log fixtures/readonly-schema)]
      (is (= :audit-log (:entity-type result)) "Has correct entity type")
      (is (= #{} (:operations result)) "Has empty operations set")
      (is (nil? (:create-schema result)) "No create schema")
      (is (nil? (:update-schema result)) "No update schema")
      (is (nil? (:delete-schema result)) "No delete schema"))))

;; T008: Test deriving create input schema (required fields only)
(deftest derive-create-input-schema-test
  (testing "derive-input-schema for create includes required fields, excludes ID"
    (let [schema fixtures/user-with-mutations-schema
          result (parser/derive-input-schema schema :create)]
      (is (vector? result) "Returns a Malli schema vector")
      (is (= :map (first result)) "Is a map schema")
      ;; Create schema should have username, email, created-at but NOT id
      (let [fields (into #{} (map first (rest result)))]
        (is (contains? fields :username) "Has username field")
        (is (contains? fields :email) "Has email field")
        (is (not (contains? fields :id)) "Does not have id field")))))

;; T009: Test deriving update input schema (all fields optional)
(deftest derive-update-input-schema-test
  (testing "derive-input-schema for update makes all fields optional, excludes ID"
    (let [schema fixtures/user-with-mutations-schema
          result (parser/derive-input-schema schema :update)]
      (is (vector? result) "Returns a Malli schema vector")
      (is (= :map (first result)) "Is a map schema")
      ;; Update schema should have optional username, email, created-at but NOT id
      (let [fields (rest result)]
        (is (every? #(and (vector? %) (map? (second %)) (:optional (second %))) fields)
            "All fields are optional")
        (is (not (some #(= :id (first %)) fields)) "Does not have id field")))))

;; T010: Test deriving delete input schema (ID only)
(deftest derive-delete-input-schema-test
  (testing "derive-input-schema for delete includes only ID field"
    (let [schema fixtures/user-with-mutations-schema
          result (parser/derive-input-schema schema :delete)]
      (is (vector? result) "Returns a Malli schema vector")
      (is (= :map (first result)) "Is a map schema")
      ;; Delete schema should have only id field
      (let [fields (into #{} (map first (rest result)))]
        (is (= #{:id} fields) "Has only id field")))))

;; T011: Test handling invalid schema format
(deftest invalid-schema-format-test
  (testing "parse-mutations handles invalid schema gracefully"
    (is (thrown? Exception
                 (parser/parse-mutations :invalid "not-a-schema"))
        "Throws exception for invalid schema")))

;; T012: Test handling invalid operation type
(deftest invalid-operation-type-test
  (testing "derive-input-schema rejects invalid operation types"
    (let [schema fixtures/user-with-mutations-schema]
      (is (thrown? Exception
                   (parser/derive-input-schema schema :invalid-op))
          "Throws exception for invalid operation"))))

;; T013-T015: Additional helper function tests
(deftest get-mutation-property-test
  (testing "get-mutation-property extracts mutations from schema properties"
    (is (= #{:create :update :delete}
           (parser/get-mutation-property fixtures/user-with-mutations-schema))
        "Extracts all mutations")
    (is (= #{:create :update}
           (parser/get-mutation-property fixtures/post-with-partial-mutations-schema))
        "Extracts subset of mutations")
    (is (= #{}
           (parser/get-mutation-property fixtures/readonly-schema))
        "Returns empty set for no mutations")))

