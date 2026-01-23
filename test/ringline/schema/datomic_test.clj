(ns ringline.schema.datomic-test
  "Contract tests for Datomic schema generation"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
            [ringline.schema.datomic :as datomic]
            [ringline.fixtures :as fixtures]))

(deftest generate-schema-test
  (testing "generate-schema creates valid Datomic attributes"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (datomic/generate-schema parsed)]
      (is (map? result) "Returns a map")
      (is (= :user (:source-entity result)) "Has correct source entity")
      (is (vector? (:attributes result)) "Attributes is a vector")
      (is (seq (:attributes result)) "Has attributes")))
  
  (testing "generate-schema applies correct Datomic types"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (datomic/generate-schema parsed)
          attrs (:attributes result)
          id-attr (first (filter #(= :user/id (:db/ident %)) attrs))
          username-attr (first (filter #(= :user/username (:db/ident %)) attrs))]
      (is (= :db.type/uuid (:db/valueType id-attr)) "UUID field has correct Datomic type")
      (is (= :db.type/string (:db/valueType username-attr)) "String field has correct Datomic type")))
  
  (testing "generate-schema applies correct cardinality"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (datomic/generate-schema parsed)
          attrs (:attributes result)
          id-attr (first (filter #(= :user/id (:db/ident %)) attrs))
          posts-attr (first (filter #(= :user/posts (:db/ident %)) attrs))]
      (is (= :db.cardinality/one (:db/cardinality id-attr)) "Single field has cardinality one")
      (is (= :db.cardinality/many (:db/cardinality posts-attr)) "Vector field has cardinality many")))
  
  (testing "generate-schema applies namespace from properties"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (datomic/generate-schema parsed)
          attrs (:attributes result)]
      (is (every? #(= "user" (namespace (:db/ident %))) attrs)
          "All attributes use the :ringline/datomic-ns namespace")))
  
  (testing "generate-schema handles ref types"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (datomic/generate-schema parsed)
          attrs (:attributes result)
          posts-attr (first (filter #(= :user/posts (:db/ident %)) attrs))]
      (is (= :db.type/ref (:db/valueType posts-attr)) "Ref field has ref type")))
  
  (testing "generate-schema handles schema without custom namespace"
    (let [parsed (parser/parse-schema :simple fixtures/simple-schema)
          result (datomic/generate-schema parsed)
          attrs (:attributes result)]
      (is (every? #(= "simple" (namespace (:db/ident %))) attrs)
          "Falls back to schema name as namespace"))))

(deftest generate-schemas-test
  (testing "generate-schemas handles multiple entities"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (datomic/generate-schemas parsed)]
      (is (vector? result) "Returns a vector")
      (is (= 3 (count result)) "Generates schema for all entities")
      (is (every? :source-entity result) "All have source entity")
      (is (every? :attributes result) "All have attributes")))
  
  (testing "generate-schemas preserves entity identity"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (datomic/generate-schemas parsed)
          user-schema (first (filter #(= :user (:source-entity %)) result))
          post-schema (first (filter #(= :post (:source-entity %)) result))]
      (is (some? user-schema) "User schema generated")
      (is (some? post-schema) "Post schema generated")
      (is (some #(= :user/id (:db/ident %)) (:attributes user-schema))
          "User schema has user-namespaced attributes")
      (is (some #(= :post/id (:db/ident %)) (:attributes post-schema))
          "Post schema has post-namespaced attributes"))))

(deftest schema->transaction-test
  (testing "schema->transaction converts to transaction format"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          datomic-schema (datomic/generate-schema parsed)
          result (datomic/schema->transaction datomic-schema)]
      (is (vector? result) "Returns a vector")
      (is (seq result) "Has transaction data")
      (is (every? map? result) "All items are maps")
      (is (every? :db/ident result) "All have :db/ident")
      (is (every? :db/valueType result) "All have :db/valueType")
      (is (every? :db/cardinality result) "All have :db/cardinality")))
  
  (testing "schema->transaction preserves all attribute properties"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          datomic-schema (datomic/generate-schema parsed)
          result (datomic/schema->transaction datomic-schema)
          id-attr (first (filter #(= :user/id (:db/ident %)) result))]
      (is (= :db.type/uuid (:db/valueType id-attr)))
      (is (= :db.cardinality/one (:db/cardinality id-attr)))))
  
  (testing "schema->transaction output is ready for Datomic transact"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          datomic-schema (datomic/generate-schema parsed)
          result (datomic/schema->transaction datomic-schema)]
      ;; Verify it has the structure Datomic expects
      (is (every? #(and (keyword? (:db/ident %))
                        (keyword? (:db/valueType %))
                        (keyword? (:db/cardinality %)))
                  result)
          "All attributes have required Datomic keys as keywords"))))

