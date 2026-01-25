(ns ringline.schema.lacinia-test
  "Contract tests for Lacinia GraphQL schema generation"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
            [ringline.schema.lacinia :as lacinia]
            [ringline.fixtures :as fixtures]))

(deftest generate-schema-test
  (testing "generate-schema creates GraphQL object type"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)]
      (is (map? result) "Returns a map")
      (is (contains? result :objects) "Has :objects key")
      (is (map? (:objects result)) "Objects is a map")))
  
  (testing "generate-schema creates object with correct fields"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          user-obj (get-in result [:objects :User])]
      (is (some? user-obj) "User object exists")
      (is (contains? user-obj :fields) "Has :fields")
      (is (map? (:fields user-obj)) "Fields is a map")))
  
  (testing "generate-schema applies correct GraphQL types"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          fields (get-in result [:objects :User :fields])]
      (is (some? (get fields :id)) "Has id field")
      (is (some? (get fields :username)) "Has username field")
      (is (some? (get fields :email)) "Has email field")))
  
  (testing "generate-schema creates query root for entities marked as query-root"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)]
      (is (contains? result :queries) "Has :queries key")
      (is (map? (:queries result)) "Queries is a map")
      (is (some? (get-in result [:queries :user])) "Has user query")))
  
  (testing "generate-schema adds searchable fields as query arguments"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          user-query (get-in result [:queries :user])]
      (is (contains? user-query :args) "Query has args")
      (is (map? (:args user-query)) "Args is a map")))
  
  (testing "generate-schema handles relationships as GraphQL object references"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          result (lacinia/generate-schema parsed)
          posts-field (get-in result [:objects :User :fields :posts])]
      (is (some? posts-field) "Has posts field for relationship")))
  
  (testing "generate-schema handles schema without query-root property"
    (let [parsed (parser/parse-schema :simple fixtures/simple-schema)
          result (lacinia/generate-schema parsed)]
      (is (contains? result :objects) "Has objects")
      (is (or (empty? (:queries result))
              (nil? (:queries result)))
          "No queries for non-query-root entity"))))

(deftest generate-schemas-test
  (testing "generate-schemas merges multiple entities into single schema"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)]
      (is (map? result) "Returns a map")
      (is (contains? result :objects) "Has :objects")
      (is (contains? result :queries) "Has :queries")))
  
  (testing "generate-schemas includes all entity object types"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)
          objects (:objects result)]
      (is (contains? objects :User) "Has User object")
      (is (contains? objects :Post) "Has Post object")
      (is (contains? objects :Comment) "Has Comment object")))
  
  (testing "generate-schemas includes queries for all query-root entities"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)
          queries (:queries result)]
      (is (some? (get queries :user)) "Has user query")
      (is (some? (get queries :post)) "Has post query")))
  
  (testing "generate-schemas preserves relationships across entities"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          result (lacinia/generate-schemas parsed)
          user-posts (get-in result [:objects :User :fields :posts])
          post-author (get-in result [:objects :Post :fields :author])]
      (is (some? user-posts) "User has posts field")
      (is (some? post-author) "Post has author field"))))

(deftest attach-resolvers-test
  (testing "attach-resolvers adds resolver functions to queries"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          schema (lacinia/generate-schema parsed)
          resolvers {:user (fn [ctx args val] {:id "123"})}
          result (lacinia/attach-resolvers schema resolvers)]
      (is (map? result) "Returns a map")
      (is (contains? result :queries) "Has queries")))
  
  (testing "attach-resolvers preserves existing schema structure"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          schema (lacinia/generate-schema parsed)
          resolvers {:user (fn [ctx args val] {:id "123"})}
          result (lacinia/attach-resolvers schema resolvers)]
      (is (= (:objects schema) (:objects result)) "Objects unchanged")
      (is (contains? result :queries) "Queries preserved")))
  
  (testing "attach-resolvers handles empty resolver map"
    (let [parsed (parser/parse-schema :user fixtures/user-schema)
          schema (lacinia/generate-schema parsed)
          result (lacinia/attach-resolvers schema {})]
      (is (map? result) "Returns a map")
      (is (= (:objects schema) (:objects result)) "Schema structure preserved"))))

;; ============================================================================
;; T015: generate-custom-query-schema Tests (NEW API - name passed separately)
;; ============================================================================

(deftest generate-custom-query-schema-test
  (testing "generate-custom-query-schema converts CustomQueryDefinition to Lacinia query schema"
    (let [query-name :searchUsers
          query-def {:args [:map [:query :string] [:limit {:optional true} :int]]
                     :return-type :User
                     :description "Search users by query string"}
          result (lacinia/generate-custom-query-schema query-name query-def)]
      (is (map? result) "Returns a map")
      (is (= :User (:type result)) "Has correct return type")
      (is (map? (:args result)) "Has args map")
      (is (= "Search users by query string" (:description result)) "Has description")))

  (testing "generate-custom-query-schema handles query without description"
    (let [query-name :getActiveUsers
          query-def {:args [:map]
                     :return-type :User}
          result (lacinia/generate-custom-query-schema query-name query-def)]
      (is (map? result) "Returns a map")
      (is (nil? (:description result)) "No description when not provided")))

  (testing "generate-custom-query-schema adds placeholder resolver"
    (let [query-name :searchUsers
          query-def {:args [:map [:query :string]]
                     :return-type :User}
          result (lacinia/generate-custom-query-schema query-name query-def)]
      (is (some? (:resolve result)) "Has resolve function")
      (is (= :custom-resolver-placeholder (:resolve result)) "Placeholder resolver added"))))

;; ============================================================================
;; T016: malli-args->lacinia-args Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest malli-args->lacinia-args-test
  (testing "malli-args->lacinia-args converts Malli map schema to Lacinia args"
    (let [malli-args [:map [:query :string] [:limit :int]]
          result (lacinia/malli-args->lacinia-args malli-args)]
      (is (map? result) "Returns a map")
      (is (contains? result :query) "Has query arg")
      (is (contains? result :limit) "Has limit arg")
      (is (= 'String (get-in result [:query :type])) "Query is String type")
      (is (= 'Int (get-in result [:limit :type])) "Limit is Int type")))

  (testing "malli-args->lacinia-args handles optional fields"
    (let [malli-args [:map [:query :string] [:limit {:optional true} :int]]
          result (lacinia/malli-args->lacinia-args malli-args)]
      (is (nil? (get-in result [:query :optional])) "Required field has no optional flag")
      (is (true? (get-in result [:limit :optional])) "Optional field marked as optional")))

  (testing "malli-args->lacinia-args handles empty args map"
    (let [malli-args [:map]
          result (lacinia/malli-args->lacinia-args malli-args)]
      (is (map? result) "Returns a map")
      (is (empty? result) "Empty map for no args")))

  (testing "malli-args->lacinia-args converts various Malli types to GraphQL types"
    (let [malli-args [:map
                      [:str-field :string]
                      [:int-field :int]
                      [:bool-field :boolean]
                      [:uuid-field :uuid]]
          result (lacinia/malli-args->lacinia-args malli-args)]
      (is (= 'String (get-in result [:str-field :type])) "String type")
      (is (= 'Int (get-in result [:int-field :type])) "Int type")
      (is (= 'Boolean (get-in result [:bool-field :type])) "Boolean type")
      (is (= 'ID (get-in result [:uuid-field :type])) "UUID becomes ID type"))))

;; ============================================================================
;; T017: resolve-type-reference Tests (TDD - Write FIRST, ensure FAIL)
;; ============================================================================

(deftest resolve-type-reference-test
  (testing "resolve-type-reference handles entity type references"
    (is (= :User (lacinia/resolve-type-reference :User)) "Entity type unchanged")
    (is (= :Order (lacinia/resolve-type-reference :Order)) "Entity type unchanged"))

  (testing "resolve-type-reference handles primitive type references"
    (is (= 'String (lacinia/resolve-type-reference :string)) "String primitive")
    (is (= 'Int (lacinia/resolve-type-reference :int)) "Int primitive")
    (is (= 'Boolean (lacinia/resolve-type-reference :boolean)) "Boolean primitive")
    (is (= 'ID (lacinia/resolve-type-reference :uuid)) "UUID becomes ID"))

  (testing "resolve-type-reference throws on invalid type reference"
    (is (thrown? Exception (lacinia/resolve-type-reference :invalid-type-123))
        "Throws on unrecognized type")))

;; ============================================================================
;; T035: generate-custom-mutation-schema Tests (NEW API - name passed separately)
;; ============================================================================

(deftest generate-custom-mutation-schema-test
  (testing "generate-custom-mutation-schema converts CustomMutationDefinition to Lacinia mutation schema"
    (let [mutation-name :approveOrder
          mutation-def {:args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                        :return-type :Order
                        :description "Approve an order with optional notes"}
          result (lacinia/generate-custom-mutation-schema mutation-name mutation-def)]
      (is (map? result) "Returns a map")
      (is (= :Order (:type result)) "Has correct return type")
      (is (map? (:args result)) "Has args map")
      (is (= "Approve an order with optional notes" (:description result)) "Has description")))

  (testing "generate-custom-mutation-schema handles mutation without description"
    (let [mutation-name :cancelOrder
          mutation-def {:args [:map [:order-id :uuid]]
                        :return-type :Order}
          result (lacinia/generate-custom-mutation-schema mutation-name mutation-def)]
      (is (map? result) "Returns a map")
      (is (nil? (:description result)) "No description when not provided")))

  (testing "generate-custom-mutation-schema adds placeholder resolver"
    (let [mutation-name :approveOrder
          mutation-def {:args [:map [:order-id :uuid]]
                        :return-type :Order}
          result (lacinia/generate-custom-mutation-schema mutation-name mutation-def)]
      (is (some? (:resolve result)) "Has resolve function")
      (is (= :custom-resolver-placeholder (:resolve result)) "Placeholder resolver added"))))

;; ============================================================================
;; T048-T049: Conflict Resolution Tests (NEW API - root-level custom operations)
;; ============================================================================

(deftest custom-query-overrides-auto-generated-test
  (testing "Custom query with same name as auto-generated query overrides it"
    ;; Create a schema with query-root (auto-generates :user query)
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/searchable-fields [:username]}
                       [:id :uuid]
                       [:username :string]]
          parsed (parser/parse-schema :User user-schema)

          ;; Define custom operation with same name as auto-generated
          custom-operations {:queries {:user {:args [:map [:id :uuid]]
                                              :return-type :User
                                              :description "Custom user query"}}}

          lacinia-schema (lacinia/generate-schemas [parsed] custom-operations)]

      ;; Verify only one :user query exists (custom overrides auto-generated)
      (is (contains? (:queries lacinia-schema) :user) "User query exists")
      (is (= "Custom user query" (get-in lacinia-schema [:queries :user :description]))
          "Custom query description present (proves custom overrode auto-generated)")
      (is (= :custom-resolver-placeholder (get-in lacinia-schema [:queries :user :resolve]))
          "Custom query has placeholder resolver")))

  (testing "Custom query with different name coexists with auto-generated query"
    (let [user-schema [:map {:ringline/datomic-ns :user
                             :ringline/query-root true
                             :ringline/searchable-fields [:username]}
                       [:id :uuid]
                       [:username :string]]
          parsed (parser/parse-schema :User user-schema)

          ;; Define custom operation with different name
          custom-operations {:queries {:searchUsers {:args [:map [:query :string]]
                                                     :return-type :User}}}

          lacinia-schema (lacinia/generate-schemas [parsed] custom-operations)]

      ;; Both queries should exist
      (is (contains? (:queries lacinia-schema) :user) "Auto-generated :user query exists")
      (is (contains? (:queries lacinia-schema) :searchUsers) "Custom :searchUsers query exists")
      (is (= 2 (count (:queries lacinia-schema))) "Exactly 2 queries"))))

(deftest mixed-operations-schema-generation-test
  (testing "Schema with both auto-generated queries and custom mutations"
    (let [order-schema [:map {:ringline/datomic-ns :order
                              :ringline/query-root true
                              :ringline/searchable-fields [:status]}
                        [:id :uuid]
                        [:status :string]]
          parsed (parser/parse-schema :Order order-schema)

          ;; Define custom mutation
          custom-operations {:mutations {:approveOrder {:args [:map [:order-id :uuid]]
                                                        :return-type :Order}}}

          lacinia-schema (lacinia/generate-schemas [parsed] custom-operations)]

      ;; Verify both auto-generated query and custom mutation exist
      (is (contains? (:queries lacinia-schema) :order) "Auto-generated :order query exists")
      (is (contains? (:mutations lacinia-schema) :approveOrder) "Custom :approveOrder mutation exists")
      (is (= 1 (count (:queries lacinia-schema))) "Exactly 1 query")
      (is (= 1 (count (:mutations lacinia-schema))) "Exactly 1 mutation")))

  (testing "Schema with both custom queries and custom mutations"
    (let [user-schema [:map {:ringline/datomic-ns :user}
                       [:id :uuid]
                       [:username :string]]
          parsed (parser/parse-schema :User user-schema)

          ;; Define both custom queries and mutations
          custom-operations {:queries {:searchUsers {:args [:map [:query :string]]
                                                     :return-type :User}}
                             :mutations {:banUser {:args [:map [:user-id :uuid]]
                                                   :return-type :User}}}

          lacinia-schema (lacinia/generate-schemas [parsed] custom-operations)]

      ;; Verify both custom operations exist
      (is (contains? (:queries lacinia-schema) :searchUsers) "Custom :searchUsers query exists")
      (is (contains? (:mutations lacinia-schema) :banUser) "Custom :banUser mutation exists")
      (is (= 1 (count (:queries lacinia-schema))) "Exactly 1 query")
      (is (= 1 (count (:mutations lacinia-schema))) "Exactly 1 mutation"))))

