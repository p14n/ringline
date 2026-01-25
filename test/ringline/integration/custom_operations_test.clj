(ns ringline.integration.custom-operations-test
  "Integration tests for custom query and mutation operations end-to-end workflow"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
            [ringline.schema.lacinia :as lacinia]
            [ringline.core :as core]))

;; ============================================================================
;; T018: Custom Query End-to-End Integration Tests (TDD - Write FIRST)
;; ============================================================================

(deftest custom-query-end-to-end-test
  (testing "Custom query workflow: define → parse → generate schema → attach resolver → execute"
    ;; Step 1: Define schema with custom query
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User
                                                     :description "Search users by query"}}
                       [:id :uuid]
                       [:username :string]
                       [:email :string]]
          
          ;; Step 2: Parse schema
          parsed (parser/parse-schema :User user-schema)]
      
      ;; Verify custom query was parsed
      (is (some? (:custom-query parsed)) "Custom query extracted during parsing")
      (is (= :searchUsers (get-in parsed [:custom-query :name])) "Custom query name correct")
      
      ;; Step 3: Generate Lacinia schema
      (let [lacinia-schema (lacinia/generate-schema parsed)]
        
        ;; Verify custom query in schema
        (is (contains? (:queries lacinia-schema) :searchUsers) "Custom query in Lacinia schema")
        (is (= :User (get-in lacinia-schema [:queries :searchUsers :type])) "Custom query return type correct")
        
        ;; Step 4: Attach custom resolver
        (let [custom-resolver (fn [ctx args value]
                                ;; Mock resolver that returns search results
                                [{:id "user-1" :username "alice" :email "alice@example.com"}
                                 {:id "user-2" :username "bob" :email "bob@example.com"}])
              resolvers {:searchUsers custom-resolver}
              schema-with-resolvers (lacinia/attach-resolvers lacinia-schema resolvers)]
          
          ;; Verify resolver attached
          (is (fn? (get-in schema-with-resolvers [:queries :searchUsers :resolve])) 
              "Custom resolver function attached")
          (is (not= :custom-resolver-placeholder 
                    (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
              "Placeholder resolver replaced with actual resolver")))))
  
  (testing "Custom query merges with auto-generated queries"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}}
                       [:id :uuid]
                       [:username :string]]
          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)]
      
      ;; Both auto-generated and custom queries should exist
      (is (contains? (:queries lacinia-schema) :user) "Auto-generated query exists")
      (is (contains? (:queries lacinia-schema) :searchUsers) "Custom query exists")
      (is (>= (count (:queries lacinia-schema)) 2) "At least 2 queries (auto + custom)")))
  
  (testing "Custom query without resolver has placeholder"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}}
                       [:id :uuid]]
          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)]
      
      ;; Verify placeholder resolver
      (is (= :custom-resolver-placeholder 
             (get-in lacinia-schema [:queries :searchUsers :resolve]))
          "Placeholder resolver before attachment"))))

(deftest custom-query-validation-test
  (testing "Framework initialization validates custom query definitions"
    ;; This test will verify that init-framework validates custom queries
    ;; Will be implemented when core/init-framework is extended
    (let [invalid-schema [:map {:ringline/custom-query {:args [:map [:query :string]]}}
                          [:id :uuid]]]
      ;; Should throw because :name and :return-type are missing
      (is (thrown? Exception (parser/parse-custom-query :User invalid-schema))
          "Invalid custom query definition throws during parsing"))))

;; ============================================================================
;; T036: Custom Mutation End-to-End Integration Tests (TDD - Write FIRST)
;; ============================================================================

(deftest custom-mutation-end-to-end-test
  (testing "Custom mutation workflow: define → parse → generate schema → attach resolver → execute"
    ;; Step 1: Define schema with custom mutation
    (let [order-schema [:map {:ringline/datomic-ns :order
                              :ringline/custom-mutation {:name :approveOrder
                                                         :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                                                         :return-type :Order
                                                         :description "Approve an order with optional notes"}}
                        [:id :uuid]
                        [:status :string]
                        [:total :int]]

          ;; Step 2: Parse schema
          parsed (parser/parse-schema :Order order-schema)]

      ;; Verify custom mutation was parsed
      (is (some? (:custom-mutation parsed)) "Custom mutation extracted during parsing")
      (is (= :approveOrder (get-in parsed [:custom-mutation :name])) "Custom mutation name correct")

      ;; Step 3: Generate Lacinia schema
      (let [lacinia-schema (lacinia/generate-schema parsed)]

        ;; Verify custom mutation in schema
        (is (contains? (:mutations lacinia-schema) :approveOrder) "Custom mutation in Lacinia schema")
        (is (= :Order (get-in lacinia-schema [:mutations :approveOrder :type])) "Custom mutation return type correct")

        ;; Step 4: Attach custom resolver
        (let [custom-resolver (fn [ctx args value]
                                ;; Mock resolver that approves the order
                                {:id (:order-id args)
                                 :status "approved"
                                 :total 100
                                 :notes (:notes args)})
              resolvers {:approveOrder custom-resolver}
              schema-with-resolvers (lacinia/attach-resolvers lacinia-schema resolvers)]

          ;; Verify resolver attached
          (is (fn? (get-in schema-with-resolvers [:mutations :approveOrder :resolve]))
              "Custom resolver function attached")
          (is (not= :custom-resolver-placeholder
                    (get-in schema-with-resolvers [:mutations :approveOrder :resolve]))
              "Placeholder resolver replaced with actual resolver")))))

  (testing "Custom mutation without resolver has placeholder"
    (let [order-schema [:map {:ringline/datomic-ns :order
                              :ringline/custom-mutation {:name :approveOrder
                                                         :args [:map [:order-id :uuid]]
                                                         :return-type :Order}}
                        [:id :uuid]]
          parsed (parser/parse-schema :Order order-schema)
          lacinia-schema (lacinia/generate-schema parsed)]

      ;; Verify placeholder resolver
      (is (= :custom-resolver-placeholder
             (get-in lacinia-schema [:mutations :approveOrder :resolve]))
          "Placeholder resolver before attachment"))))

(deftest custom-mutation-validation-test
  (testing "Framework validates custom mutation definitions"
    (let [invalid-schema [:map {:ringline/custom-mutation {:args [:map [:order-id :uuid]]}}
                          [:id :uuid]]]
      ;; Should throw because :name and :return-type are missing
      (is (thrown? Exception (parser/parse-custom-mutation :Order invalid-schema))
          "Invalid custom mutation definition throws during parsing"))))

;; ============================================================================
;; T050: Mixed Auto-Generated and Custom Operations Integration Tests
;; ============================================================================

(deftest mixed-auto-and-custom-operations-test
  (testing "Multiple schemas with mix of auto-generated and custom operations"
    ;; Define multiple schemas with different combinations
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/searchable-fields [:username]
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}}
                       [:id :uuid]
                       [:username :string]]

          order-schema [:map {:ringline/datomic-ns :order
                              :ringline/query-root true
                              :ringline/searchable-fields [:status]
                              :ringline/custom-mutation {:name :approveOrder
                                                         :args [:map [:order-id :uuid]]
                                                         :return-type :Order}}
                         [:id :uuid]
                         [:status :string]]

          ;; Parse both schemas
          parsed-user (parser/parse-schema :User user-schema)
          parsed-order (parser/parse-schema :Order order-schema)

          ;; Generate individual schemas
          user-lacinia (lacinia/generate-schema parsed-user)
          order-lacinia (lacinia/generate-schema parsed-order)

          ;; Merge schemas
          merged-schema (lacinia/generate-schemas [parsed-user parsed-order])]

      ;; Verify User schema has both auto-generated and custom queries
      (is (contains? (:queries user-lacinia) :user) "User has auto-generated :user query")
      (is (contains? (:queries user-lacinia) :searchUsers) "User has custom :searchUsers query")

      ;; Verify Order schema has auto-generated query and custom mutation
      (is (contains? (:queries order-lacinia) :order) "Order has auto-generated :order query")
      (is (contains? (:mutations order-lacinia) :approveOrder) "Order has custom :approveOrder mutation")

      ;; Verify merged schema contains all operations
      (is (contains? (:queries merged-schema) :user) "Merged has :user query")
      (is (contains? (:queries merged-schema) :searchUsers) "Merged has :searchUsers query")
      (is (contains? (:queries merged-schema) :order) "Merged has :order query")
      (is (contains? (:mutations merged-schema) :approveOrder) "Merged has :approveOrder mutation")
      (is (= 3 (count (:queries merged-schema))) "Merged has 3 queries total")
      (is (= 1 (count (:mutations merged-schema))) "Merged has 1 mutation total")))

  (testing "Conflict resolution: custom operation overrides auto-generated with same name"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/searchable-fields [:username]
                             :ringline/custom-query {:name :user  ; Same as auto-generated
                                                     :args [:map [:id :uuid]]
                                                     :return-type :User
                                                     :description "Custom user lookup"}}
                       [:id :uuid]
                       [:username :string]]

          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)]

      ;; Verify custom query overrode auto-generated
      (is (= 1 (count (:queries lacinia-schema))) "Only 1 query (custom overrode auto-generated)")
      (is (= "Custom user lookup" (get-in lacinia-schema [:queries :user :description]))
          "Custom query description proves it overrode auto-generated")))

  (testing "Attach resolvers to mixed operations"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/searchable-fields [:username]
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}
                             :ringline/custom-mutation {:name :banUser
                                                        :args [:map [:user-id :uuid]]
                                                        :return-type :User}}
                       [:id :uuid]
                       [:username :string]]

          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)

          ;; Define resolvers for all operations
          resolvers {:user (fn [ctx args value] {:id "1" :username "alice"})
                     :searchUsers (fn [ctx args value] [{:id "1" :username "alice"}])
                     :banUser (fn [ctx args value] {:id (:user-id args) :username "banned"})}

          schema-with-resolvers (lacinia/attach-resolvers lacinia-schema resolvers)]

      ;; Verify all resolvers attached
      (is (fn? (get-in schema-with-resolvers [:queries :user :resolve]))
          "Auto-generated query resolver attached")
      (is (fn? (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
          "Custom query resolver attached")
      (is (fn? (get-in schema-with-resolvers [:mutations :banUser :resolve]))
          "Custom mutation resolver attached"))))

;; ============================================================================
;; T051: Resolver Validation with Mixed Operations Tests
;; ============================================================================

(deftest resolver-validation-with-mixed-operations-test
  (testing "Missing resolver for custom operation leaves placeholder"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}}
                       [:id :uuid]]

          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)]

      ;; Verify placeholder resolver exists
      (is (= :custom-resolver-placeholder
             (get-in lacinia-schema [:queries :searchUsers :resolve]))
          "Custom operation without resolver has placeholder")))

  (testing "Partial resolver attachment (some operations get resolvers, others don't)"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/searchable-fields [:username]
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}
                             :ringline/custom-mutation {:name :banUser
                                                        :args [:map [:user-id :uuid]]
                                                        :return-type :User}}
                       [:id :uuid]
                       [:username :string]]

          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)

          ;; Only attach resolver for searchUsers, not banUser
          resolvers {:searchUsers (fn [ctx args value] [])}
          schema-with-resolvers (lacinia/attach-resolvers lacinia-schema resolvers)]

      ;; Verify searchUsers has real resolver
      (is (fn? (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
          "searchUsers has real resolver")
      (is (not= :custom-resolver-placeholder
                (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
          "searchUsers resolver is not placeholder")

      ;; Verify banUser still has placeholder
      (is (= :custom-resolver-placeholder
             (get-in schema-with-resolvers [:mutations :banUser :resolve]))
          "banUser still has placeholder resolver")))

  (testing "Resolver map with extra resolvers (not matching any operation) is ignored"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/custom-query {:name :searchUsers
                                                     :args [:map [:query :string]]
                                                     :return-type :User}}
                       [:id :uuid]]

          parsed (parser/parse-schema :User user-schema)
          lacinia-schema (lacinia/generate-schema parsed)

          ;; Provide resolvers including one that doesn't match any operation
          resolvers {:searchUsers (fn [ctx args value] [])
                     :nonExistentOperation (fn [ctx args value] nil)}
          schema-with-resolvers (lacinia/attach-resolvers lacinia-schema resolvers)]

      ;; Verify searchUsers got its resolver
      (is (fn? (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
          "searchUsers has resolver")

      ;; Verify no extra operations were added
      (is (= 1 (count (:queries schema-with-resolvers)))
          "No extra queries added from resolver map"))))

