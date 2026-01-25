(ns ringline.schema.parser-test
  "Contract tests for Malli schema parser"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
            [ringline.fixtures :as fixtures]))

(deftest parse-schema-test
  (testing "parse-schema extracts basic field information"
    (let [result (parser/parse-schema :User fixtures/user-schema)]
      (is (map? result) "Returns a map")
      (is (= :User (:schema-name result)) "Extracts schema name")
      (is (vector? (:fields result)) "Fields is a vector")
      (is (seq (:fields result)) "Fields is non-empty")))
  
  (testing "parse-schema extracts field types correctly"
    (let [result (parser/parse-schema :User fixtures/user-schema)
          fields (:fields result)
          id-field (first (filter #(= :id (:name %)) fields))
          username-field (first (filter #(= :username (:name %)) fields))]
      (is (= :uuid (:type id-field)) "UUID field has correct type")
      (is (= :string (:type username-field)) "String field has correct type")))
  
  (testing "parse-schema extracts custom properties"
    (let [result (parser/parse-schema :User fixtures/user-schema)
          props (:properties result)]
      (is (map? props) "Properties is a map")
      (is (= :user (:ringline/datomic-ns props)) "Extracts datomic-ns property")
      (is (true? (:ringline/query-root props)) "Extracts query-root property")
      (is (= [:email :username] (:ringline/searchable props)) "Extracts searchable property")))
  
  (testing "parse-schema identifies relationships"
    (let [result (parser/parse-schema :User fixtures/user-schema)
          relationships (:relationships result)
          posts-rel (first (filter #(= :posts (:field %)) relationships))]
      (is (vector? relationships) "Relationships is a vector")
      (is (seq relationships) "Has at least one relationship")
      (is (= :posts (:field posts-rel)) "Identifies posts field as relationship")
      (is (= :many (:cardinality posts-rel)) "Detects many cardinality for vector refs")))
  
  (testing "parse-schema handles simple schema without properties"
    (let [result (parser/parse-schema :Simple fixtures/simple-schema)]
      (is (= :Simple (:schema-name result)))
      (is (empty? (:properties result)) "Properties is empty map")
      (is (empty? (:relationships result)) "No relationships")))
  
  (testing "parse-schema detects cardinality from collection types"
    (let [result (parser/parse-schema :Post fixtures/post-schema)
          fields (:fields result)
          tags-field (first (filter #(= :tags (:name %)) fields))]
      (is (= :many (:cardinality tags-field)) "Vector of strings has many cardinality"))))

(deftest parse-schemas-test
  (testing "parse-schemas handles multiple entities"
    (let [result (parser/parse-schemas fixtures/test-schemas)]
      (is (vector? result) "Returns a vector")
      (is (= 3 (count result)) "Parses all three schemas")
      (is (every? map? result) "All results are maps")
      (is (every? :schema-name result) "All have schema-name")))
  
  (testing "parse-schemas resolves relationship targets"
    (let [result (parser/parse-schemas fixtures/test-schemas)
          user-schema (first (filter #(= :user (:schema-name %)) result))
          user-rels (:relationships user-schema)
          posts-rel (first (filter #(= :posts (:field %)) user-rels))]
      (is (some? posts-rel) "User has posts relationship")
      (is (= :post (:target posts-rel)) "Posts relationship targets Post entity")))
  
  (testing "parse-schemas handles bidirectional relationships"
    (let [result (parser/parse-schemas fixtures/test-schemas)
          post-schema (first (filter #(= :post (:schema-name %)) result))
          post-rels (:relationships post-schema)
          author-rel (first (filter #(= :author (:field %)) post-rels))]
      (is (some? author-rel) "Post has author relationship")
      (is (= :user (:target author-rel)) "Author relationship targets User entity")
      (is (= :one (:cardinality author-rel)) "Author has one cardinality")))
  
  (testing "parse-schemas preserves all entity metadata"
    (let [result (parser/parse-schemas fixtures/test-schemas)
          user-schema (first (filter #(= :user (:schema-name %)) result))]
      (is (seq (:fields user-schema)) "Preserves fields")
      (is (seq (:properties user-schema)) "Preserves properties")
      (is (seq (:relationships user-schema)) "Preserves relationships"))))

;; ============================================================================
;; T013: parse-custom-query Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest parse-custom-query-test
  (testing "parse-custom-query extracts custom query definition from schema properties"
    (let [schema [:map {:ringline/custom-query {:name :searchUsers
                                                 :args [:map [:query :string]]
                                                 :return-type :User
                                                 :description "Search users"}}
                  [:id :uuid]]
          result (parser/parse-custom-query :User schema)]
      (is (map? result) "Returns a map")
      (is (= :searchUsers (:name result)) "Extracts query name")
      (is (= [:map [:query :string]] (:args result)) "Extracts args schema")
      (is (= :User (:return-type result)) "Extracts return type")
      (is (= "Search users" (:description result)) "Extracts description")))

  (testing "parse-custom-query returns nil when no custom query defined"
    (let [schema [:map [:id :uuid]]
          result (parser/parse-custom-query :User schema)]
      (is (nil? result) "Returns nil when no custom query")))

  (testing "parse-custom-query throws on invalid custom query definition"
    (let [schema [:map {:ringline/custom-query {:args [:map [:query :string]]}}
                  [:id :uuid]]]
      (is (thrown? Exception (parser/parse-custom-query :User schema))
          "Throws when required fields missing"))))

;; ============================================================================
;; T014: parse-schema with :custom-query field Tests (TDD - Write FIRST)
;; ============================================================================

(deftest parse-schema-with-custom-query-test
  (testing "parse-schema includes :custom-query field in ParsedSchema"
    (let [schema [:map {:ringline/datomic-ns :user
                        :ringline/custom-query {:name :searchUsers
                                                :args [:map [:query :string]]
                                                :return-type :User}}
                  [:id :uuid]]
          result (parser/parse-schema :User schema)]
      (is (contains? result :custom-query) "ParsedSchema has :custom-query field")
      (is (map? (:custom-query result)) "Custom query is a map")
      (is (= :searchUsers (get-in result [:custom-query :name])) "Custom query name extracted")))

  (testing "parse-schema sets :custom-query to nil when not defined"
    (let [schema [:map {:ringline/datomic-ns :user} [:id :uuid]]
          result (parser/parse-schema :User schema)]
      (is (contains? result :custom-query) "ParsedSchema has :custom-query field")
      (is (nil? (:custom-query result)) "Custom query is nil when not defined"))))

;; ============================================================================
;; T033: parse-custom-mutation Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest parse-custom-mutation-test
  (testing "parse-custom-mutation extracts custom mutation definition from schema properties"
    (let [schema [:map {:ringline/custom-mutation {:name :approveOrder
                                                    :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                                                    :return-type :Order
                                                    :description "Approve an order"}}
                  [:id :uuid]]
          result (parser/parse-custom-mutation :Order schema)]
      (is (map? result) "Returns a map")
      (is (= :approveOrder (:name result)) "Extracts mutation name")
      (is (= [:map [:order-id :uuid] [:notes {:optional true} :string]] (:args result)) "Extracts args schema")
      (is (= :Order (:return-type result)) "Extracts return type")
      (is (= "Approve an order" (:description result)) "Extracts description")))

  (testing "parse-custom-mutation returns nil when no custom mutation defined"
    (let [schema [:map [:id :uuid]]
          result (parser/parse-custom-mutation :Order schema)]
      (is (nil? result) "Returns nil when no custom mutation")))

  (testing "parse-custom-mutation throws on invalid custom mutation definition"
    (let [schema [:map {:ringline/custom-mutation {:args [:map [:order-id :uuid]]}}
                  [:id :uuid]]]
      (is (thrown? Exception (parser/parse-custom-mutation :Order schema))
          "Throws when required fields missing"))))

;; ============================================================================
;; T034: parse-schema with :custom-mutation field Tests (TDD - Write FIRST)
;; ============================================================================

(deftest parse-schema-with-custom-mutation-test
  (testing "parse-schema includes :custom-mutation field in ParsedSchema"
    (let [schema [:map {:ringline/datomic-ns :order
                        :ringline/custom-mutation {:name :approveOrder
                                                   :args [:map [:order-id :uuid]]
                                                   :return-type :Order}}
                  [:id :uuid]]
          result (parser/parse-schema :Order schema)]
      (is (contains? result :custom-mutation) "ParsedSchema has :custom-mutation field")
      (is (map? (:custom-mutation result)) "Custom mutation is a map")
      (is (= :approveOrder (get-in result [:custom-mutation :name])) "Custom mutation name extracted")))

  (testing "parse-schema sets :custom-mutation to nil when not defined"
    (let [schema [:map {:ringline/datomic-ns :order} [:id :uuid]]
          result (parser/parse-schema :Order schema)]
      (is (contains? result :custom-mutation) "ParsedSchema has :custom-mutation field")
      (is (nil? (:custom-mutation result)) "Custom mutation is nil when not defined"))))

