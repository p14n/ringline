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

