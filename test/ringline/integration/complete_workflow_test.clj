(ns ringline.integration.complete-workflow-test
  "Integration test for complete end-to-end workflow"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.core :as core]
            [ringline.query.converter :as converter]
            [ringline.response.transformer :as transformer]
            [ringline.fixtures :as fixtures]))

(deftest complete-workflow-test
  (testing "Complete workflow: Malli → Datomic + Lacinia → Query → Response"
    (let [;; Step 1: Initialize framework with Malli schemas
          schemas {:user fixtures/user-schema
                   :post fixtures/post-schema}
          framework (core/init-framework schemas {})

          ;; Verify framework initialization
          _ (do
              (is (map? framework) "Framework initialized")
              (is (seq (:datomic framework)) "Datomic schemas generated")
              (is (map? (:lacinia framework)) "Lacinia schema generated")
              (is (= 2 (count (:parsed framework))) "All schemas parsed"))

          ;; Step 2: Verify Datomic schema structure
          datomic-schemas (:datomic framework)
          _ (do
              (is (every? #(contains? % :source-entity) datomic-schemas)
                  "All Datomic schemas have source entity")
              (is (every? #(contains? % :attributes) datomic-schemas)
                  "All Datomic schemas have attributes"))

          ;; Step 3: Verify Lacinia schema structure
          lacinia-schema (:lacinia framework)
          _ (do
              (is (contains? (:objects lacinia-schema) :User) "Has User object")
              (is (contains? (:objects lacinia-schema) :Post) "Has Post object")
              (is (contains? (:queries lacinia-schema) :user) "Has user query")
              (is (contains? (:queries lacinia-schema) :post) "Has post query"))

          ;; Step 4: Simulate GraphQL query
          lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {} :email {}}  ; Only select id and email, not username
                        :arguments {:email "test@example.com"}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)

          ;; Verify query context
          _ (do
              (is (= :User (:entity-type query-ctx)) "Correct entity type")
              (is (seq (:selections query-ctx)) "Has selections")
              (is (= "test@example.com" (get-in query-ctx [:arguments :email]))
                  "Arguments extracted"))

          ;; Step 5: Convert to Datomic pull pattern
          pull-result (converter/pull-with-args query-ctx {})

          ;; Verify pull pattern
          _ (do
              (is (vector? (:pattern pull-result)) "Pull pattern is a vector")
              (is (seq (:where-clauses pull-result)) "Where clauses generated"))

          ;; Step 6: Simulate Datomic query result
          datomic-entity {:user/id "123"
                          :user/email "test@example.com"
                          :user/username "testuser"}

          ;; Step 7: Transform response to GraphQL format
          graphql-response (transformer/transform-with-selections datomic-entity query-ctx {})]

      ;; Verify final response
      (is (map? graphql-response) "Response is a map")
      (is (= "123" (:id graphql-response)) "ID transformed correctly")
      (is (= "test@example.com" (:email graphql-response)) "Email transformed correctly")
      (is (not (contains? graphql-response :username))
          "Non-selected fields filtered out")))

  (testing "Complete workflow with nested relationships"
    (let [;; Initialize framework
          schemas fixtures/test-schemas
          framework (core/init-framework schemas {})

          ;; Simulate GraphQL query with nested selection
          lacinia-ctx {:com.walmartlabs.lacinia/selection
                       {:selections {:id {}
                                     :email {}
                                     :posts {:selections {:id {} :title {}}}}
                        :arguments {}}}
          query-ctx (converter/build-query-context lacinia-ctx :User)

          ;; Convert to pull pattern
          pull-result (converter/graphql->pull query-ctx)

          ;; Simulate Datomic result with nested entities
          datomic-entity {:user/id "123"
                          :user/email "test@example.com"
                          :user/posts [{:post/id "p1" :post/title "First Post"}
                                       {:post/id "p2" :post/title "Second Post"}]}

          ;; Transform to GraphQL
          graphql-response (transformer/transform-with-selections datomic-entity query-ctx {})]

      ;; Verify nested response
      (is (contains? graphql-response :posts) "Has posts field")
      (is (vector? (:posts graphql-response)) "Posts is a vector")
      (is (= 2 (count (:posts graphql-response))) "Has all posts")
      (is (every? #(contains? % :id) (:posts graphql-response)) "All posts have :id")
      (is (every? #(contains? % :title) (:posts graphql-response)) "All posts have :title")))

  (testing "Complete workflow with all three schemas"
    (let [;; Initialize with all test schemas
          framework (core/init-framework fixtures/test-schemas {})
          lacinia-schema (:lacinia framework)]

      ;; Verify all entities are present
      (is (= 3 (count (:objects lacinia-schema))) "Has all three object types")
      (is (contains? (:objects lacinia-schema) :User) "Has User")
      (is (contains? (:objects lacinia-schema) :Post) "Has Post")
      (is (contains? (:objects lacinia-schema) :Comment) "Has Comment")

      ;; Verify relationships are preserved
      (is (some? (get-in lacinia-schema [:objects :User :fields :posts]))
          "User has posts relationship")
      (is (some? (get-in lacinia-schema [:objects :Post :fields :author]))
          "Post has author relationship")
      (is (some? (get-in lacinia-schema [:objects :Comment :fields :post]))
          "Comment has post relationship")
      (is (some? (get-in lacinia-schema [:objects :Comment :fields :author]))
          "Comment has author relationship"))))

(deftest mutation-workflow-test
  (testing "Complete mutation workflow: Schema → Lacinia mutations → Execution"
    (let [;; Step 1: Initialize framework with mutation-enabled schema
          schemas {:user fixtures/user-with-mutations-schema}
          framework (core/init-framework schemas {})

          ;; Verify framework initialization with mutations
          _ (do
              (is (map? framework) "Framework initialized")
              (is (seq (:mutations framework)) "Mutations parsed")
              (is (= 1 (count (:mutations framework))) "One entity with mutations"))

          ;; Step 2: Verify Lacinia mutation schema structure
          lacinia-schema (:lacinia framework)
          _ (do
              (is (contains? lacinia-schema :mutations) "Has mutations key")
              (is (contains? lacinia-schema :input-objects) "Has input-objects key")
              (is (contains? (:mutations lacinia-schema) :createUser) "Has createUser mutation")
              (is (contains? (:mutations lacinia-schema) :updateUser) "Has updateUser mutation")
              (is (contains? (:mutations lacinia-schema) :deleteUser) "Has deleteUser mutation")
              (is (contains? (:input-objects lacinia-schema) :CreateUserInput) "Has CreateUserInput")
              (is (contains? (:input-objects lacinia-schema) :UpdateUserInput) "Has UpdateUserInput"))

          ;; Step 3: Verify mutation definitions
          create-mutation (get-in lacinia-schema [:mutations :createUser])
          update-mutation (get-in lacinia-schema [:mutations :updateUser])
          delete-mutation (get-in lacinia-schema [:mutations :deleteUser])]

      ;; Verify create mutation structure
      (is (= :User (:type create-mutation)) "Create returns User type")
      (is (contains? (:args create-mutation) :input) "Create has input arg")

      ;; Verify update mutation structure
      (is (= :User (:type update-mutation)) "Update returns User type")
      (is (contains? (:args update-mutation) :input) "Update has input arg")

      ;; Verify delete mutation structure
      (is (= :Boolean (:type delete-mutation)) "Delete returns Boolean type")
      (is (contains? (:args delete-mutation) :input) "Delete has input arg")))

  (testing "Mutation resolver creation and attachment"
    (let [;; Initialize framework
          schemas {:user fixtures/user-with-mutations-schema}
          framework (core/init-framework schemas {})

          ;; Create individual mutation resolver with mock connection
          mock-db-conn {:type :mock-connection
                        :transact-fn (fn [tx-data]
                                       {:db-before {}
                                        :db-after {}
                                        :tx-data tx-data
                                        :tempids {}})}
          create-resolver (core/create-mutation-resolver
                           :user
                           :create
                           mock-db-conn
                           fixtures/user-with-mutations-schema)]

      ;; Verify resolver is a function
      (is (fn? create-resolver) "Resolver is a function")

      ;; Test resolver execution (will use mock transaction)
      (let [context {}
            args {:input {:username "alice"
                          :email "alice@example.com"
                          :created-at 1234567890}}
            result (create-resolver context args nil)]

        ;; Verify result structure - mutation resolvers return entity data for GraphQL
        (is (map? result) "Result is a map")
        (is (contains? result :id) "Has id field (generated UUID)")
        (is (contains? result :username) "Has username field")
        (is (= "alice" (:username result)) "Username matches input"))))

  (testing "Attach mutation resolvers to schema"
    (let [;; Initialize framework
          schemas {:user fixtures/user-with-mutations-schema}
          framework (core/init-framework schemas {})

          ;; Attach resolvers
          mock-db-conn nil
          lacinia-with-resolvers (core/attach-mutation-resolvers
                                  (:lacinia framework)
                                  schemas
                                  mock-db-conn)

          ;; Verify resolvers attached
          create-mutation (get-in lacinia-with-resolvers [:mutations :createUser])
          update-mutation (get-in lacinia-with-resolvers [:mutations :updateUser])
          delete-mutation (get-in lacinia-with-resolvers [:mutations :deleteUser])]

      ;; Verify all mutations have resolvers
      (is (fn? (:resolve create-mutation)) "createUser has resolver")
      (is (fn? (:resolve update-mutation)) "updateUser has resolver")
      (is (fn? (:resolve delete-mutation)) "deleteUser has resolver"))))

