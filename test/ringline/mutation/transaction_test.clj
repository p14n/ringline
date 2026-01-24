(ns ringline.mutation.transaction-test
  "Contract tests for Datomic transaction converter"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.mutation.transaction :as tx]
            [ringline.fixtures :as fixtures]))

;; T036: Test converting create mutation to Datomic transaction
(deftest create-mutation-to-transaction-test
  (testing "mutation-input->transaction generates create transaction with tempid"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username "alice"
                        :email "alice@example.com"
                        :created-at 1234567890}}
          result (tx/mutation-input->transaction input fixtures/user-with-mutations-schema)]
      (is (map? result) "Returns a map")
      (is (contains? result :db/id) "Has :db/id")
      (is (contains? result :user/username) "Has namespaced username")
      (is (= "alice" (:user/username result)) "Username value correct")
      (is (= "alice@example.com" (:user/email result)) "Email value correct")
      (is (some? (:user/id result)) "Has generated UUID for :user/id")))

  (testing "create transaction uses tempid for :db/id"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username "bob"
                        :email "bob@example.com"
                        :created-at 1234567890}}
          result (tx/mutation-input->transaction input fixtures/user-with-mutations-schema)
          db-id (:db/id result)]
      (is (string? db-id) "db/id is a tempid string")
      (is (re-matches #"tempid-.*" db-id) "db/id matches tempid pattern"))))

;; T037: Test converting update mutation to Datomic transaction
(deftest update-mutation-to-transaction-test
  (testing "mutation-input->transaction generates update transaction with lookup ref"
    (let [user-id (java.util.UUID/randomUUID)
          input {:operation :update
                 :entity-type :user
                 :entity-id user-id
                 :data {:username "alice-updated"
                        :email "alice-new@example.com"}}
          result (tx/mutation-input->transaction input fixtures/user-with-mutations-schema)]
      (is (map? result) "Returns a map")
      (is (vector? (:db/id result)) "db/id is a lookup ref vector")
      (is (= [:user/id user-id] (:db/id result)) "Lookup ref uses :user/id")
      (is (= "alice-updated" (:user/username result)) "Username updated")
      (is (= "alice-new@example.com" (:user/email result)) "Email updated")))

  (testing "update transaction only includes provided fields"
    (let [user-id (java.util.UUID/randomUUID)
          input {:operation :update
                 :entity-type :user
                 :entity-id user-id
                 :data {:username "alice-updated"}}
          result (tx/mutation-input->transaction input fixtures/user-with-mutations-schema)]
      (is (contains? result :user/username) "Has username")
      (is (not (contains? result :user/email)) "Does not have email (not provided)"))))

;; T038: Test converting delete mutation to Datomic transaction
(deftest delete-mutation-to-transaction-test
  (testing "mutation-input->transaction generates delete transaction with retractEntity"
    (let [user-id (java.util.UUID/randomUUID)
          input {:operation :delete
                 :entity-type :user
                 :entity-id user-id}
          result (tx/mutation-input->transaction input fixtures/user-with-mutations-schema)]
      (is (vector? result) "Returns a vector for retractEntity")
      (is (= :db/retractEntity (first result)) "First element is :db/retractEntity")
      (is (vector? (second result)) "Second element is lookup ref")
      (is (= [:user/id user-id] (second result)) "Lookup ref uses :user/id"))))

;; T039: Test field name conversion (kebab-case to namespace/kebab-case)
(deftest field-name-conversion-test
  (testing "convert-field-name adds Datomic namespace"
    (is (= :user/username (tx/convert-field-name :user :username)) "Simple field")
    (is (= :user/created-at (tx/convert-field-name :user :created-at)) "Kebab-case field")
    (is (= :post/published? (tx/convert-field-name :post :published?)) "Boolean field"))

  (testing "convert-field-name handles special :id field"
    (is (= :user/id (tx/convert-field-name :user :id)) "ID field gets namespace")))

;; T040: Test data value conversion (GraphQL to Datomic)
(deftest data-value-conversion-test
  (testing "convert-value handles different types"
    (is (= "alice" (tx/convert-value :string "alice")) "String value")
    (is (= 42 (tx/convert-value :int 42)) "Int value")
    (is (= true (tx/convert-value :boolean true)) "Boolean value")
    (is (instance? java.util.UUID (tx/convert-value :uuid (java.util.UUID/randomUUID))) "UUID value")))

;; T041: Test tempid generation
(deftest tempid-generation-test
  (testing "generate-tempid creates unique tempid strings"
    (let [tempid1 (tx/generate-tempid)
          tempid2 (tx/generate-tempid)]
      (is (string? tempid1) "Returns a string")
      (is (re-matches #"tempid-.*" tempid1) "Matches tempid pattern")
      (is (not= tempid1 tempid2) "Generates unique tempids"))))

;; T042: Test lookup ref generation
(deftest lookup-ref-generation-test
  (testing "generate-lookup-ref creates correct lookup ref"
    (let [user-id (java.util.UUID/randomUUID)
          result (tx/generate-lookup-ref :user user-id)]
      (is (vector? result) "Returns a vector")
      (is (= 2 (count result)) "Has two elements")
      (is (= :user/id (first result)) "First element is namespaced :id")
      (is (= user-id (second result)) "Second element is the UUID"))))

;; T043: Test handling missing entity-id for update
(deftest missing-entity-id-update-test
  (testing "mutation-input->transaction throws error for update without entity-id"
    (let [input {:operation :update
                 :entity-type :user
                 :data {:username "alice"}}]
      (is (thrown? Exception
                   (tx/mutation-input->transaction input fixtures/user-with-mutations-schema))
          "Throws exception for missing entity-id"))))

;; T044: Test handling missing entity-id for delete
(deftest missing-entity-id-delete-test
  (testing "mutation-input->transaction throws error for delete without entity-id"
    (let [input {:operation :delete
                 :entity-type :user}]
      (is (thrown? Exception
                   (tx/mutation-input->transaction input fixtures/user-with-mutations-schema))
          "Throws exception for missing entity-id"))))

;; T045: Test handling missing data for create
(deftest missing-data-create-test
  (testing "mutation-input->transaction throws error for create without data"
    (let [input {:operation :create
                 :entity-type :user}]
      (is (thrown? Exception
                   (tx/mutation-input->transaction input fixtures/user-with-mutations-schema))
          "Throws exception for missing data"))))

;; T046: Test handling invalid operation type
(deftest invalid-operation-test
  (testing "mutation-input->transaction throws error for invalid operation"
    (let [input {:operation :invalid
                 :entity-type :user
                 :data {:username "alice"}}]
      (is (thrown? Exception
                   (tx/mutation-input->transaction input fixtures/user-with-mutations-schema))
          "Throws exception for invalid operation"))))

