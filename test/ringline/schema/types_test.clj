(ns ringline.schema.types-test
  "Tests for type mappings between Malli, Datomic, and GraphQL"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.types :as types]))

;; ============================================================================
;; Existing Type Mapping Tests
;; ============================================================================

(deftest existing-malli-to-datomic-mappings
  (testing "Existing Malli → Datomic type mappings"
    (is (= :db.type/string (types/malli-type->datomic-type :string)))
    (is (= :db.type/long (types/malli-type->datomic-type :int)))
    (is (= :db.type/double (types/malli-type->datomic-type :double)))
    (is (= :db.type/boolean (types/malli-type->datomic-type :boolean)))
    (is (= :db.type/uuid (types/malli-type->datomic-type :uuid)))
    (is (= :db.type/instant (types/malli-type->datomic-type :inst)))
    (is (= :db.type/keyword (types/malli-type->datomic-type :keyword)))
    (is (= :db.type/ref (types/malli-type->datomic-type :ref)))))

(deftest existing-malli-to-graphql-mappings
  (testing "Existing Malli → GraphQL type mappings"
    (is (= 'String (types/malli-type->graphql-type :string)))
    (is (= 'Int (types/malli-type->graphql-type :int)))
    (is (= 'Float (types/malli-type->graphql-type :double)))
    (is (= 'Boolean (types/malli-type->graphql-type :boolean)))
    (is (= 'ID (types/malli-type->graphql-type :uuid)))
    (is (= 'String (types/malli-type->graphql-type :inst)))
    (is (= 'String (types/malli-type->graphql-type :keyword)))))

;; ============================================================================
;; T005: Date Type Mapping Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest date-type-mapping-test
  (testing "Date type mapping: :time/local-date → :db.type/instant"
    (is (= :db.type/instant (types/malli-type->datomic-type :time/local-date))
        "Date fields should map to Datomic instant type (stored as midnight UTC)"))
  
  (testing "Date type mapping: :time/local-date → 'Date (GraphQL)"
    (is (= 'Date (types/malli-type->graphql-type :time/local-date))
        "Date fields should map to GraphQL Date scalar type")))

;; ============================================================================
;; T006: DateTime Type Mapping Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest datetime-type-mapping-test
  (testing "DateTime type mapping: :time/offset-date-time → :db.type/instant"
    (is (= :db.type/instant (types/malli-type->datomic-type :time/offset-date-time))
        "DateTime fields should map to Datomic instant type (dual-attribute for timezone)"))
  
  (testing "DateTime type mapping: :time/offset-date-time → 'DateTime (GraphQL)"
    (is (= 'DateTime (types/malli-type->graphql-type :time/offset-date-time))
        "DateTime fields should map to GraphQL DateTime scalar type")))

;; ============================================================================
;; T007: Enum Type Mapping Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest enum-type-mapping-test
  (testing "Enum type mapping: :enum → :db.type/keyword"
    (is (= :db.type/keyword (types/malli-type->datomic-type :enum))
        "Enum fields should map to Datomic keyword type"))
  
  (testing "Enum type mapping: :enum → 'String (GraphQL - will be refined to enum type)"
    ;; Note: The actual GraphQL type will be generated dynamically based on enum options
    ;; The base mapping returns 'String as a placeholder
    (is (= 'String (types/malli-type->graphql-type :enum))
        "Enum fields should have base mapping to String (refined during schema generation)")))

;; ============================================================================
;; T008: Decimal Type Mapping Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest decimal-type-mapping-test
  (testing "Decimal type mapping: :decimal → :db.type/bigdec"
    (is (= :db.type/bigdec (types/malli-type->datomic-type :decimal))
        "Decimal fields should map to Datomic bigdec type"))
  
  (testing "Decimal type mapping: :decimal → 'Decimal (GraphQL)"
    (is (= 'Decimal (types/malli-type->graphql-type :decimal))
        "Decimal fields should map to GraphQL Decimal scalar type")))

;; ============================================================================
;; Collection Type Tests (Existing Functionality)
;; ============================================================================

(deftest collection-type-detection
  (testing "Collection type detection for cardinality :many"
    (is (true? (types/collection-type? :vector)))
    (is (true? (types/collection-type? :sequential)))
    (is (true? (types/collection-type? :set)))
    (is (false? (types/collection-type? :string)))
    (is (false? (types/collection-type? :int)))))

;; ============================================================================
;; T003: CustomQueryDefinition Schema Validation Tests (TDD - Write FIRST)
;; ============================================================================

(deftest custom-query-definition-schema-test
  (testing "CustomQueryDefinition schema validates valid query definitions"
    (let [valid-query {:name :searchUsers
                       :args [:map [:query :string] [:limit {:optional true} :int]]
                       :return-type :User
                       :description "Search users by query string"}]
      (is (nil? (types/validate-custom-query-definition valid-query))
          "Valid custom query definition should pass validation")))

  (testing "CustomQueryDefinition schema requires :name field"
    (let [invalid-query {:args [:map [:query :string]]
                         :return-type :User}]
      (is (some? (types/validate-custom-query-definition invalid-query))
          "Custom query definition without :name should fail validation")))

  (testing "CustomQueryDefinition schema requires :args field"
    (let [invalid-query {:name :searchUsers
                         :return-type :User}]
      (is (some? (types/validate-custom-query-definition invalid-query))
          "Custom query definition without :args should fail validation")))

  (testing "CustomQueryDefinition schema requires :return-type field"
    (let [invalid-query {:name :searchUsers
                         :args [:map [:query :string]]}]
      (is (some? (types/validate-custom-query-definition invalid-query))
          "Custom query definition without :return-type should fail validation")))

  (testing "CustomQueryDefinition schema allows optional :description field"
    (let [query-without-desc {:name :searchUsers
                              :args [:map [:query :string]]
                              :return-type :User}]
      (is (nil? (types/validate-custom-query-definition query-without-desc))
          "Custom query definition without :description should pass validation"))))

;; ============================================================================
;; T004: CustomMutationDefinition Schema Validation Tests (TDD - Write FIRST)
;; ============================================================================

(deftest custom-mutation-definition-schema-test
  (testing "CustomMutationDefinition schema validates valid mutation definitions"
    (let [valid-mutation {:name :approveOrder
                          :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                          :return-type :Order
                          :description "Approve an order with optional notes"}]
      (is (nil? (types/validate-custom-mutation-definition valid-mutation))
          "Valid custom mutation definition should pass validation")))

  (testing "CustomMutationDefinition schema requires :name field"
    (let [invalid-mutation {:args [:map [:order-id :uuid]]
                            :return-type :Order}]
      (is (some? (types/validate-custom-mutation-definition invalid-mutation))
          "Custom mutation definition without :name should fail validation")))

  (testing "CustomMutationDefinition schema requires :args field"
    (let [invalid-mutation {:name :approveOrder
                            :return-type :Order}]
      (is (some? (types/validate-custom-mutation-definition invalid-mutation))
          "Custom mutation definition without :args should fail validation")))

  (testing "CustomMutationDefinition schema requires :return-type field"
    (let [invalid-mutation {:name :approveOrder
                            :args [:map [:order-id :uuid]]}]
      (is (some? (types/validate-custom-mutation-definition invalid-mutation))
          "Custom mutation definition without :return-type should fail validation")))

  (testing "CustomMutationDefinition schema allows optional :description field"
    (let [mutation-without-desc {:name :approveOrder
                                 :args [:map [:order-id :uuid]]
                                 :return-type :Order}]
      (is (nil? (types/validate-custom-mutation-definition mutation-without-desc))
          "Custom mutation definition without :description should pass validation"))))

