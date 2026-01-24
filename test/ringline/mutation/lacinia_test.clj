(ns ringline.mutation.lacinia-test
  "Contract tests for Lacinia mutation schema generator"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.mutation.lacinia :as lacinia]
            [ringline.mutation.parser :as parser]
            [ringline.fixtures :as fixtures]))

;; T018: Test generating mutations for entity with all operations
(deftest generate-mutations-all-operations-test
  (testing "generate-mutation-schemas creates all mutation fields for entity with all operations"
    (let [parsed (parser/parse-mutations :user fixtures/user-with-mutations-schema)
          result (lacinia/generate-mutation-schemas parsed)]
      (is (map? result) "Returns a map")
      (is (contains? result :mutations) "Has :mutations key")
      (is (contains? result :input-objects) "Has :input-objects key")
      (is (contains? (:mutations result) :createUser) "Has createUser mutation")
      (is (contains? (:mutations result) :updateUser) "Has updateUser mutation")
      (is (contains? (:mutations result) :deleteUser) "Has deleteUser mutation")
      (is (contains? (:input-objects result) :CreateUserInput) "Has CreateUserInput")
      (is (contains? (:input-objects result) :UpdateUserInput) "Has UpdateUserInput"))))

;; T019: Test generating mutations for entity with subset of operations
(deftest generate-mutations-subset-test
  (testing "generate-mutation-schemas creates only specified mutations"
    (let [parsed (parser/parse-mutations :post fixtures/post-with-partial-mutations-schema)
          result (lacinia/generate-mutation-schemas parsed)]
      (is (contains? (:mutations result) :createPost) "Has createPost mutation")
      (is (contains? (:mutations result) :updatePost) "Has updatePost mutation")
      (is (not (contains? (:mutations result) :deletePost)) "Does not have deletePost mutation"))))

;; T020: Test mutation naming conventions (camelCase)
(deftest mutation-naming-convention-test
  (testing "mutation-name generates correct camelCase names"
    (is (= :createUser (lacinia/mutation-name :user :create)) "Create mutation name")
    (is (= :updateUser (lacinia/mutation-name :user :update)) "Update mutation name")
    (is (= :deleteUser (lacinia/mutation-name :user :delete)) "Delete mutation name")
    (is (= :createBlogPost (lacinia/mutation-name :blog-post :create)) "Handles kebab-case entity")))

;; T021: Test input type naming conventions (PascalCase + Input suffix)
(deftest input-type-naming-convention-test
  (testing "input-type-name generates correct PascalCase names with Input suffix"
    (is (= :CreateUserInput (lacinia/input-type-name :user :create)) "Create input type")
    (is (= :UpdateUserInput (lacinia/input-type-name :user :update)) "Update input type")
    (is (= :DeleteUserInput (lacinia/input-type-name :user :delete)) "Delete input type")
    (is (= :CreateBlogPostInput (lacinia/input-type-name :blog-post :create)) "Handles kebab-case")))

;; T022: Test create mutation with required fields
(deftest create-mutation-structure-test
  (testing "generate-mutation-field for create has correct structure"
    (let [parsed (parser/parse-mutations :user fixtures/user-with-mutations-schema)
          mutation (lacinia/generate-mutation-field parsed :create)]
      (is (map? mutation) "Returns a map")
      (is (contains? mutation :type) "Has :type key")
      (is (contains? mutation :args) "Has :args key")
      (is (= :User (:type mutation)) "Return type is entity type")
      (is (map? (:args mutation)) "Args is a map")
      (is (contains? (:args mutation) :input) "Has :input argument"))))

;; T023: Test update mutation with optional fields
(deftest update-mutation-structure-test
  (testing "generate-mutation-field for update has correct structure"
    (let [parsed (parser/parse-mutations :user fixtures/user-with-mutations-schema)
          mutation (lacinia/generate-mutation-field parsed :update)]
      (is (map? mutation) "Returns a map")
      (is (= :User (:type mutation)) "Return type is entity type")
      (is (contains? (:args mutation) :input) "Has :input argument"))))

;; T024: Test delete mutation with ID argument
(deftest delete-mutation-structure-test
  (testing "generate-mutation-field for delete has correct structure"
    (let [parsed (parser/parse-mutations :user fixtures/user-with-mutations-schema)
          mutation (lacinia/generate-mutation-field parsed :delete)]
      (is (map? mutation) "Returns a map")
      (is (= :Boolean (:type mutation)) "Return type is Boolean")
      (is (contains? (:args mutation) :input) "Has :input argument"))))

;; T025: Test input object generation from Malli schema
(deftest generate-input-object-test
  (testing "generate-input-object creates Lacinia input object from Malli schema"
    (let [malli-schema [:map
                        [:username :string]
                        [:email :string]
                        [:created-at :int]]
          result (lacinia/generate-input-object :CreateUserInput malli-schema)]
      (is (map? result) "Returns a map")
      (is (contains? result :fields) "Has :fields key")
      (is (map? (:fields result)) "Fields is a map")
      (is (contains? (:fields result) :username) "Has username field")
      (is (contains? (:fields result) :email) "Has email field")
      (is (contains? (:fields result) :created_at) "Has created_at field (snake_case)"))))

;; T026: Test type mapping (Malli → GraphQL)
(deftest malli-to-graphql-type-mapping-test
  (testing "malli-type->graphql-type maps Malli types to GraphQL types"
    (is (= 'String (lacinia/malli-type->graphql-type :string)) "String mapping")
    (is (= 'Int (lacinia/malli-type->graphql-type :int)) "Int mapping")
    (is (= 'Float (lacinia/malli-type->graphql-type :double)) "Float mapping")
    (is (= 'Boolean (lacinia/malli-type->graphql-type :boolean)) "Boolean mapping")
    (is (= 'ID (lacinia/malli-type->graphql-type :uuid)) "ID mapping")))

;; T027: Test handling multiple entities
(deftest multiple-entities-test
  (testing "generate-mutation-schemas handles multiple entities"
    (let [user-parsed (parser/parse-mutations :user fixtures/user-with-mutations-schema)
          post-parsed (parser/parse-mutations :post fixtures/post-with-partial-mutations-schema)
          user-result (lacinia/generate-mutation-schemas user-parsed)
          post-result (lacinia/generate-mutation-schemas post-parsed)
          combined-mutations (merge (:mutations user-result) (:mutations post-result))
          combined-inputs (merge (:input-objects user-result) (:input-objects post-result))]
      (is (contains? combined-mutations :createUser) "Has user mutations")
      (is (contains? combined-mutations :createPost) "Has post mutations")
      (is (= 5 (count combined-mutations)) "Has correct total count (3 user + 2 post)")
      (is (contains? combined-inputs :CreateUserInput) "Has user input objects")
      (is (contains? combined-inputs :CreatePostInput) "Has post input objects"))))

;; T028: Test merging with existing Lacinia schema
(deftest merge-with-existing-schema-test
  (testing "Generated mutations can be merged with existing schema"
    (let [parsed (parser/parse-mutations :user fixtures/user-with-mutations-schema)
          result (lacinia/generate-mutation-schemas parsed)
          existing-schema {:mutations {:customMutation {:type :String}}}
          merged (update existing-schema :mutations merge (:mutations result))]
      (is (contains? (:mutations merged) :customMutation) "Preserves existing mutations")
      (is (contains? (:mutations merged) :createUser) "Adds new mutations")
      (is (= 4 (count (:mutations merged))) "Has correct total count"))))

