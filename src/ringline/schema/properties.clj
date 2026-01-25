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

;; ============================================================================
;; Rich Comment Block - Property Usage Examples
;; ============================================================================

(comment
  ;; Entity schema properties define how entities are mapped to Datomic and GraphQL

  ;; ============================================================================
  ;; Example 1: Basic Entity with Datomic Namespace
  ;; ============================================================================

  (def user-schema
    [:map {:ringline/datomic-ns :user}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  ;; Generates Datomic attributes:
  ;; :user/id, :user/username, :user/email

  ;; ============================================================================
  ;; Example 2: Query Root Entity
  ;; ============================================================================

  (def user-schema-queryable
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true}
     [:id :uuid]
     [:username :string]])

  ;; Generates GraphQL query:
  ;; query { user(id: "...") { id username } }

  ;; ============================================================================
  ;; Example 3: Searchable Fields
  ;; ============================================================================

  (def user-schema-searchable
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true
           :ringline/searchable-fields [:username :email]}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  ;; Generates GraphQL query with search args:
  ;; query { user(username: "alice") { id username email } }
  ;; query { user(email: "alice@example.com") { id username email } }

  ;; ============================================================================
  ;; Example 4: Entity Relationships
  ;; ============================================================================

  (def post-schema
    [:map {:ringline/datomic-ns :post}
     [:id :uuid]
     [:title :string]
     [:author-id {:ringline/ref-to :user} :uuid]])

  ;; Generates Datomic reference:
  ;; :post/author-id {:db/valueType :db.type/ref}

  ;; Generates GraphQL relationship:
  ;; type Post { id: ID! title: String! author: User }

  ;; ============================================================================
  ;; Example 5: Auto-Generated Mutations
  ;; ============================================================================

  (def user-schema-with-mutations
    [:map {:ringline/datomic-ns :user
           :ringline/mutations #{:create :update :delete}}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  ;; Generates GraphQL mutations:
  ;; mutation { createUser(input: {...}) { id username email } }
  ;; mutation { updateUser(id: "...", input: {...}) { id username email } }
  ;; mutation { deleteUser(id: "...") { id } }

  ;; ============================================================================
  ;; IMPORTANT: Custom Queries and Mutations
  ;; ============================================================================

  ;; Custom queries and mutations are NOT defined in entity schema properties.
  ;; They are defined separately at the root level and passed to init-framework.

  ;; WRONG (old API - DO NOT USE):
  ;; (def user-schema
  ;;   [:map {:ringline/custom-query {:name :searchUsers ...}}
  ;;    [:id :uuid]])

  ;; CORRECT (new API):
  ;; (def user-schema
  ;;   [:map {:ringline/datomic-ns :user}
  ;;    [:id :uuid]])
  ;;
  ;; (def custom-operations
  ;;   {:queries {:searchUsers {:args [:map [:query :string]]
  ;;                            :return-type :User}}})
  ;;
  ;; (init-framework {:User user-schema}
  ;;                 {:custom-operations custom-operations
  ;;                  :resolvers {:searchUsers search-users-fn}})

  ;; See ringline.core and ringline.schema.lacinia for custom operations examples.

  )

