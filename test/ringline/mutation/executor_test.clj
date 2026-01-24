(ns ringline.mutation.executor-test
  "Contract tests for mutation executor"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.mutation.executor :as executor]
            [ringline.fixtures :as fixtures]))

;; Mock Datomic connection for testing
(def mock-db-conn
  {:type :mock-connection
   :db-after {:entities {}}})

;; T054: Test successful create mutation execution
(deftest execute-create-mutation-test
  (testing "execute-mutation successfully creates entity"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username "alice"
                        :email "alice@example.com"
                        :created-at 1234567890}}
          result (executor/execute-mutation input fixtures/user-with-mutations-schema mock-db-conn)]
      (is (map? result) "Returns a map")
      (is (true? (:success result)) "Success is true")
      (is (= :create (:operation result)) "Operation is :create")
      (is (= :user (:entity-type result)) "Entity type is :user")
      (is (map? (:data result)) "Has data map")
      (is (some? (:entity-id result)) "Has entity-id")
      (is (int? (:timestamp result)) "Has timestamp")))

  (testing "create mutation returns entity data"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username "alice"
                        :email "alice@example.com"
                        :created-at 1234567890}}
          result (executor/execute-mutation input fixtures/user-with-mutations-schema mock-db-conn)
          data (:data result)]
      (is (= "alice" (:username data)) "Has username in data")
      (is (= "alice@example.com" (:email data)) "Has email in data"))))

;; T055: Test successful update mutation execution
(deftest execute-update-mutation-test
  (testing "execute-mutation successfully updates entity"
    (let [user-id (java.util.UUID/randomUUID)
          input {:operation :update
                 :entity-type :user
                 :entity-id user-id
                 :data {:email "newemail@example.com"}}
          result (executor/execute-mutation input fixtures/user-with-mutations-schema mock-db-conn)]
      (is (true? (:success result)) "Success is true")
      (is (= :update (:operation result)) "Operation is :update")
      (is (= user-id (:entity-id result)) "Has correct entity-id")
      (is (map? (:data result)) "Has data map"))))

;; T056: Test successful delete mutation execution
(deftest execute-delete-mutation-test
  (testing "execute-mutation successfully deletes entity"
    (let [user-id (java.util.UUID/randomUUID)
          input {:operation :delete
                 :entity-type :user
                 :entity-id user-id}
          result (executor/execute-mutation input fixtures/user-with-mutations-schema mock-db-conn)]
      (is (true? (:success result)) "Success is true")
      (is (= :delete (:operation result)) "Operation is :delete")
      (is (= user-id (:entity-id result)) "Has correct entity-id")
      (is (nil? (:data result)) "No data for delete"))))

;; T057: Test validation error handling
(deftest validation-error-handling-test
  (testing "execute-mutation returns validation errors for invalid input"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username 123  ; Invalid: should be string
                        :email "alice@example.com"}}
          result (executor/execute-mutation input fixtures/user-with-mutations-schema mock-db-conn)]
      (is (false? (:success result)) "Success is false")
      (is (vector? (:errors result)) "Has errors vector")
      (is (seq (:errors result)) "Errors vector is not empty")
      (is (= :VALIDATION_ERROR (:code (first (:errors result)))) "Error code is VALIDATION_ERROR"))))

;; T058: Test missing required field error
(deftest missing-required-field-test
  (testing "execute-mutation returns error for missing required fields"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username "alice"}}  ; Missing email
          result (executor/execute-mutation input fixtures/user-with-mutations-schema mock-db-conn)]
      (is (false? (:success result)) "Success is false")
      (is (seq (:errors result)) "Has errors"))))

;; T059: Test operation not allowed error
(deftest operation-not-allowed-test
  (testing "execute-mutation returns error when operation not in allowed set"
    (let [input {:operation :delete
                 :entity-type :post  ; post-with-partial-mutations only allows :create :update
                 :entity-id (java.util.UUID/randomUUID)}
          result (executor/execute-mutation input fixtures/post-with-partial-mutations-schema mock-db-conn)]
      (is (false? (:success result)) "Success is false")
      (is (seq (:errors result)) "Has errors")
      (is (some #(= :VALIDATION_ERROR (:code %)) (:errors result)) "Has validation error"))))

;; T060: Test validate-mutation-input function
(deftest validate-mutation-input-test
  (testing "validate-mutation-input accepts valid create input"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username "alice"
                        :email "alice@example.com"
                        :created-at 1234567890}}
          result (executor/validate-mutation-input input fixtures/user-with-mutations-schema)]
      (is (true? (:valid? result)) "Input is valid")
      (is (empty? (:errors result)) "No errors")))

  (testing "validate-mutation-input rejects invalid input"
    (let [input {:operation :create
                 :entity-type :user
                 :data {:username 123}}  ; Invalid type
          result (executor/validate-mutation-input input fixtures/user-with-mutations-schema)]
      (is (false? (:valid? result)) "Input is invalid")
      (is (seq (:errors result)) "Has errors"))))

;; T061: Test format-success-result function
(deftest format-success-result-test
  (testing "format-success-result creates proper success result"
    (let [result (executor/format-success-result
                  :create
                  :user
                  {:username "alice" :email "alice@example.com"}
                  #uuid "123e4567-e89b-12d3-a456-426614174000")]
      (is (true? (:success result)) "Success is true")
      (is (= :create (:operation result)) "Has operation")
      (is (= :user (:entity-type result)) "Has entity-type")
      (is (map? (:data result)) "Has data")
      (is (uuid? (:entity-id result)) "Has entity-id")
      (is (int? (:timestamp result)) "Has timestamp"))))

;; T062: Test format-error-result function
(deftest format-error-result-test
  (testing "format-error-result creates proper error result"
    (let [errors [{:code :VALIDATION_ERROR :message "Invalid field"}]
          result (executor/format-error-result :create :user errors)]
      (is (false? (:success result)) "Success is false")
      (is (= :create (:operation result)) "Has operation")
      (is (= :user (:entity-type result)) "Has entity-type")
      (is (vector? (:errors result)) "Has errors vector")
      (is (= errors (:errors result)) "Errors match input")
      (is (int? (:timestamp result)) "Has timestamp"))))

;; T063: Test build-validation-error function
(deftest build-validation-error-test
  (testing "build-validation-error creates proper error map"
    (let [error (executor/build-validation-error "Invalid username" :username "123")]
      (is (= :VALIDATION_ERROR (:code error)) "Has VALIDATION_ERROR code")
      (is (= "Invalid username" (:message error)) "Has message")
      (is (= :username (:field error)) "Has field")
      (is (= "123" (:value error)) "Has value"))))

;; T064: Test check-operation-allowed function
(deftest check-operation-allowed-test
  (testing "check-operation-allowed returns true for allowed operation"
    (is (true? (executor/check-operation-allowed
                :create
                fixtures/user-with-mutations-schema))
        "Create is allowed"))

  (testing "check-operation-allowed returns false for disallowed operation"
    (is (false? (executor/check-operation-allowed
                 :delete
                 fixtures/post-with-partial-mutations-schema))
        "Delete is not allowed for post")))

