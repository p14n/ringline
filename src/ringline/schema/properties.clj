(ns ringline.schema.properties
  "Custom Malli properties for Ringline framework")

;; Custom property keywords for Malli schemas

(def datomic-ns
  "Property key for specifying Datomic namespace for entity attributes.
   
   Example: {:ringline/datomic-ns :user}
   
   This will generate Datomic attributes like :user/id, :user/name, etc."
  :ringline/datomic-ns)

(def query-root
  "Property key for marking an entity as a GraphQL query root.
   
   Example: {:ringline/query-root true}
   
   This will add the entity to the GraphQL query root with appropriate resolvers."
  :ringline/query-root)

(def searchable
  "Property key for marking fields as searchable in GraphQL queries.

   Example: {:ringline/searchable [:email :username]}

   This will generate GraphQL query arguments for searching by these fields."
  :ringline/searchable)

(def ref-to
  "Property key for marking a field as a reference to another entity.

   Example: [:author :uuid {:ringline/ref-to :user}]

   This marks the field as a relationship and will generate appropriate Datomic :db.type/ref
   and GraphQL object type references."
  :ringline/ref-to)

(def mutations
  "Property key for specifying allowed mutation operations for an entity.

   Example: {:ringline/mutations #{:create :update :delete}}

   This will generate GraphQL mutations for the specified operations.
   Valid operations: :create, :update, :delete"
  :ringline/mutations)

(def custom-query
  "Property key for defining custom GraphQL queries.

   Example: {:ringline/custom-query {:name :searchUsers
                                      :args [:map [:query :string] [:limit {:optional true} :int]]
                                      :return-type :User
                                      :description \"Search users by query string\"}}

   Value should be a map with:
   - :name (keyword) - GraphQL query name
   - :args (Malli schema) - Query argument schema
   - :return-type (keyword) - Return type reference
   - :description (string, optional) - GraphQL description"
  :ringline/custom-query)

(def custom-mutation
  "Property key for defining custom GraphQL mutations.

   Example: {:ringline/custom-mutation {:name :approveOrder
                                         :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                                         :return-type :Order
                                         :description \"Approve an order with optional notes\"}}

   Value should be a map with:
   - :name (keyword) - GraphQL mutation name
   - :args (Malli schema) - Mutation input schema
   - :return-type (keyword) - Return type reference
   - :description (string, optional) - GraphQL description"
  :ringline/custom-mutation)

;; Helper functions for property access

(defn get-datomic-ns
  "Extract the Datomic namespace from schema properties"
  [properties]
  (get properties datomic-ns))

(defn query-root?
  "Check if schema is marked as a query root"
  [properties]
  (get properties query-root false))

(defn get-searchable-fields
  "Extract searchable fields from schema properties"
  [properties]
  (get properties searchable []))

(defn get-ref-target
  "Extract the reference target entity from field properties"
  [properties]
  (get properties ref-to))

(defn get-mutations
  "Extract allowed mutation operations from schema properties.
   Returns empty set if not present."
  [properties]
  (get properties mutations #{}))

(defn get-custom-query
  "Extract custom query definition from schema properties.
   Returns nil if not present."
  [properties]
  (get properties custom-query))

(defn get-custom-mutation
  "Extract custom mutation definition from schema properties.
   Returns nil if not present."
  [properties]
  (get properties custom-mutation))

(defn custom-query?
  "Check if schema properties define a custom query"
  [properties]
  (some? (get properties custom-query)))

(defn custom-mutation?
  "Check if schema properties define a custom mutation"
  [properties]
  (some? (get properties custom-mutation)))

;; ============================================================================
;; Rich Comment Block - REPL Examples
;; ============================================================================

(comment
  ;; Example 1: Define a schema with custom query
  (def user-schema
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true
           :ringline/searchable-fields [:username :email]
           :ringline/custom-query {:name :searchUsers
                                   :args [:map [:query :string] [:limit {:optional true} :int]]
                                   :return-type :User
                                   :description "Search users by query string"}}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  ;; Extract properties
  (def props (malli.core/properties user-schema))

  ;; Check for custom query
  (custom-query? props)
  ;; => true

  (get-custom-query props)
  ;; => {:name :searchUsers
  ;;     :args [:map [:query :string] [:limit {:optional true} :int]]
  ;;     :return-type :User
  ;;     :description "Search users by query string"}

  ;; Example 2: Define a schema with custom mutation
  (def order-schema
    [:map {:ringline/datomic-ns :order
           :ringline/custom-mutation {:name :approveOrder
                                      :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                                      :return-type :Order
                                      :description "Approve an order with optional notes"}}
     [:id :uuid]
     [:status :string]
     [:total :int]])

  (def order-props (malli.core/properties order-schema))

  (custom-mutation? order-props)
  ;; => true

  (get-custom-mutation order-props)
  ;; => {:name :approveOrder
  ;;     :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
  ;;     :return-type :Order
  ;;     :description "Approve an order with optional notes"}

  ;; Example 3: Schema with both custom query and mutation
  (def product-schema
    [:map {:ringline/datomic-ns :product
           :ringline/query-root true
           :ringline/searchable-fields [:name]
           :ringline/custom-query {:name :searchProducts
                                   :args [:map [:query :string]]
                                   :return-type :Product}
           :ringline/custom-mutation {:name :discontinueProduct
                                      :args [:map [:product-id :uuid]]
                                      :return-type :Product}}
     [:id :uuid]
     [:name :string]
     [:price :int]])

  (def product-props (malli.core/properties product-schema))

  (custom-query? product-props)
  ;; => true

  (custom-mutation? product-props)
  ;; => true

  :end)

