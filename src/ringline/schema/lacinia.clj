(ns ringline.schema.lacinia
  "Generate Lacinia GraphQL schemas from parsed Malli schemas"
  (:require [malli.core :as m]
            [ringline.schema.types :as types]
            [ringline.schema.properties :as props]
            [ringline.schema.scalars :as scalars]
            [clojure.string :as str]))

;; Malli schemas for validation

(def LaciniaSchema
  "Schema for a Lacinia GraphQL schema"
  [:map
   [:objects :map]
   [:queries {:optional true} :map]
   [:mutations {:optional true} :map]
   [:enums {:optional true} :map]
   [:scalars {:optional true} :map]])

;; ============================================================================
;; T030: Custom Scalar Definitions
;; ============================================================================

(defn custom-scalars
  "Define custom GraphQL scalars for Lacinia.

  Returns map of scalar definitions with :parse and :serialize functions."
  []
  {:Date {:parse scalars/parse-date
          :serialize scalars/serialize-date}
   ;; T050: DateTime scalar with timezone support
   :DateTime {:parse scalars/parse-datetime
              :serialize scalars/serialize-datetime}
   ;; T091: Decimal scalar with precision/scale validation
   :Decimal {:parse (fn [s] (scalars/parse-decimal s {:precision 38 :scale 10}))
             :serialize scalars/serialize-decimal}})

;; ============================================================================
;; Type mapping
;; ============================================================================

(defn resolve-type-reference
  "Resolve a type reference to a GraphQL type.

   Handles both entity type references (:User, :Order) and primitive types (:string, :int).
   Entity types are returned as-is (keywords).
   Primitive types are converted to GraphQL type symbols.

   Args:
     type-ref - Type reference keyword (e.g., :User, :string, :int)

   Returns:
     GraphQL type (keyword for entities, symbol for primitives)

   Throws:
     ex-info if type reference is invalid"
  [type-ref]
  (cond
    ;; Check if it's a known primitive type
    (contains? types/malli->graphql type-ref)
    (types/malli-type->graphql-type type-ref)

    ;; Assume it's an entity type reference (PascalCase keyword like :User, :Order)
    ;; Entity types start with uppercase letter
    (and (keyword? type-ref)
         (let [name-str (name type-ref)]
           (and (seq name-str)
                (Character/isUpperCase (first name-str)))))
    type-ref

    (vector? type-ref)
    (list 'list (resolve-type-reference (second type-ref)))

    ;; Invalid type reference
    :else
    (throw (ex-info "Invalid type reference"
                    {:type-ref type-ref
                     :valid-primitives (keys types/malli->graphql)}))))

(defn malli-args->lacinia-args
  "Convert Malli argument schema to Lacinia args map.

   Transforms Malli [:map ...] schema to Lacinia argument format.
   Handles primitives, optionals, and nested maps.

   Args:
     malli-schema - Malli schema (e.g., [:map [:query :string] [:limit {:optional true} :int]])

   Returns:
     Map of arg-name to {:type GraphQLType :optional boolean}"
  [malli-schema]
  (if (and (vector? malli-schema) (= :map (first malli-schema)))
    (let [children (m/children (m/schema malli-schema))]
      (into {}
            (map (fn [[arg-name arg-properties arg-type-schema]]
                   (let [arg-type (if (keyword? arg-type-schema)
                                    arg-type-schema
                                    (m/type arg-type-schema))
                         graphql-type (resolve-type-reference arg-type)
                         optional? (:optional arg-properties)]
                     [arg-name (cond-> {:type graphql-type}
                                 optional? (assoc :optional true))]))
                 children)))
    {}))

(defn- field->graphql-type
  "Convert a field's Malli type to GraphQL (Lacinia) type.

  T031: Extended to handle :time/local-date"
  [field]
  (let [base-type (types/malli-type->graphql-type (:type field))]
    (if (= :many (:cardinality field))
      (list 'list base-type)
      base-type)))

(defn- capitalize-first
  "Capitalize the first letter of a string"
  [s]
  (if (empty? s)
    s
    (str (str/upper-case (subs s 0 1)) (subs s 1))))

(defn- keyword->graphql-name
  "Convert a keyword to a GraphQL type name (PascalCase)"
  [kw]
  (-> (name kw)
      capitalize-first
      keyword))

;; Field generation

(defn- field->graphql-field
  "Convert a parsed field to a Lacinia field definition"
  [field relationships]
  (let [field-name (:name field)
        ;; Check if this field is a relationship
        rel (first (filter #(= field-name (:field %)) relationships))]
    (if rel
      ;; Relationship field - use target entity type
      {:type (if (= :many (:cardinality field))
               (list 'list (keyword->graphql-name (:target rel)))
               (keyword->graphql-name (:target rel)))}
      ;; Regular field
      {:type (field->graphql-type field)})))

(defn- fields->graphql-fields
  "Convert all fields to Lacinia field definitions.

   Field names are used as-is (no conversion)."
  [fields relationships]
  (into {}
        (map (fn [field]
               [(:name field) (field->graphql-field field relationships)])
             fields)))

;; Object type generation

(defn- generate-object-type
  "Generate a Lacinia object type from parsed schema"
  [parsed-schema]
  (let [fields (:fields parsed-schema)
        relationships (:relationships parsed-schema)]
    {:fields (fields->graphql-fields fields relationships)}))

;; Custom query/mutation generation

(defn generate-custom-query-schema
  "Generate Lacinia query schema from a custom query definition.

   Converts custom query definition to Lacinia query schema format.

   Args:
     query-name - Keyword name of the query (e.g., :searchUsers)
     query-def - CustomQueryDefinition map with :args, :return-type, :description

   Returns:
     Lacinia query spec map, e.g., {:type :User :args {...} :resolve ...}"
  [query-name query-def]
  (let [return-type (resolve-type-reference (:return-type query-def))
        args (malli-args->lacinia-args (:args query-def))
        description (:description query-def)]
    (cond-> {:type return-type
             :args args
             :resolve :custom-resolver-placeholder}
      description (assoc :description description))))

(defn generate-custom-mutation-schema
  "Generate Lacinia mutation schema from a custom mutation definition.

   Converts custom mutation definition to Lacinia mutation schema format.

   Args:
     mutation-name - Keyword name of the mutation (e.g., :approveOrder)
     mutation-def - CustomMutationDefinition map with :args, :return-type, :description

   Returns:
     Lacinia mutation spec map, e.g., {:type :Order :args {...} :resolve ...}"
  [mutation-name mutation-def]
  (let [return-type (resolve-type-reference (:return-type mutation-def))
        args (malli-args->lacinia-args (:args mutation-def))
        description (:description mutation-def)]
    (cond-> {:type return-type
             :args args
             :resolve :custom-resolver-placeholder}
      description (assoc :description description))))

;; Query generation

(defn- generate-query-args
  "Generate query arguments from searchable fields"
  [parsed-schema]
  (let [searchable-fields (props/get-searchable-fields (:properties parsed-schema))
        fields (:fields parsed-schema)]
    (into {}
          (map (fn [field-name]
                 (let [field (first (filter #(= field-name (:name %)) fields))]
                   [field-name {:type (field->graphql-type field)}]))
               searchable-fields))))

(defn- generate-query-for-entity
  "Generate query definition for an entity marked as query-root"
  [parsed-schema]
  (let [entity-name (:schema-name parsed-schema)
        graphql-name (keyword->graphql-name entity-name)
        query-name (keyword (str/lower-case (name entity-name)))
        args (generate-query-args parsed-schema)]
    {query-name {:type graphql-name
                 :args args}}))

;; Main generation functions

(defn generate-schema
  "Generate Lacinia GraphQL schema from a parsed Malli schema.

   Generates object types and auto-generated queries for entities.

   Args:
     parsed-schema - ParsedSchema map from parser

   Returns:
     LaciniaSchema map with :objects and optionally :queries"
  [parsed-schema]
  (let [entity-name (:schema-name parsed-schema)
        graphql-name (keyword->graphql-name entity-name)
        object-type (generate-object-type parsed-schema)
        is-query-root? (props/query-root? (:properties parsed-schema))

        ;; Generate auto-generated queries
        auto-queries (when is-query-root?
                       (generate-query-for-entity parsed-schema))

        result (cond-> {:objects {graphql-name object-type}}
                 (seq auto-queries)
                 (assoc :queries auto-queries))]
    ;; Validate the result
    (when-not (m/validate LaciniaSchema result)
      (throw (ex-info "Invalid LaciniaSchema"
                      {:entity-name entity-name
                       :errors (m/explain LaciniaSchema result)})))
    result))

(defn generate-schemas
  "Generate complete Lacinia GraphQL schema from multiple parsed entities.

   Merges auto-generated schemas with custom operations.
   Custom operations override auto-generated operations with the same name.

   Args:
     parsed-schemas - Vector of ParsedSchema maps
     custom-operations - Optional map with :queries and :mutations (CustomOperations schema)

   Returns:
     Single LaciniaSchema map with all objects, queries, mutations, and custom scalars merged"
  ([parsed-schemas]
   (generate-schemas parsed-schemas nil))
  ([parsed-schemas custom-operations]
   (let [individual-schemas (map generate-schema parsed-schemas)
         auto-queries (apply merge {} (map :queries individual-schemas))
         auto-mutations (apply merge {} (map :mutations individual-schemas))

         ;; Generate custom queries
         custom-queries (when-let [queries (:queries custom-operations)]
                          (into {}
                                (map (fn [[query-name query-def]]
                                       [query-name (generate-custom-query-schema query-name query-def)])
                                     queries)))

         ;; Generate custom mutations
         custom-mutations (when-let [mutations (:mutations custom-operations)]
                            (into {}
                                  (map (fn [[mutation-name mutation-def]]
                                         [mutation-name (generate-custom-mutation-schema mutation-name mutation-def)])
                                       mutations)))

         ;; Merge (custom overrides auto-generated)
         merged {:objects (apply merge {} (map :objects individual-schemas))
                 :queries (merge auto-queries custom-queries)
                 :mutations (merge auto-mutations custom-mutations)
                 :scalars (custom-scalars)}]
     ;; Validate the result
     (when-not (m/validate LaciniaSchema merged)
       (throw (ex-info "Invalid merged LaciniaSchema"
                       {:errors (m/explain LaciniaSchema merged)})))
     merged)))

(defn attach-resolvers
  "Attach resolver functions to a Lacinia schema.

   Extended to attach resolvers to both queries and mutations.

   Args:
     lacinia-schema - LaciniaSchema map
     resolvers-map - Map of operation-name to resolver function

   Returns:
     LaciniaSchema with resolvers attached to queries and mutations"
  [lacinia-schema resolvers-map]
  (-> lacinia-schema
      (update :queries
              (fn [queries]
                (into {}
                      (map (fn [[query-name query-def]]
                             (if-let [resolver (get resolvers-map query-name)]
                               [query-name (assoc query-def :resolve resolver)]
                               [query-name query-def]))
                           queries))))
      (update :mutations
              (fn [mutations]
                (into {}
                      (map (fn [[mutation-name mutation-def]]
                             (if-let [resolver (get resolvers-map mutation-name)]
                               [mutation-name (assoc mutation-def :resolve resolver)]
                               [mutation-name mutation-def]))
                           mutations))))))

;; ============================================================================
;; Rich Comment Block - Custom Operations Examples
;; ============================================================================

(comment
  ;; Custom operations are defined at the ROOT LEVEL, not in entity schemas

  ;; ============================================================================
  ;; Example 1: Basic Custom Query
  ;; ============================================================================

  (require '[ringline.schema.parser :as parser])

  ;; Define entity schema (no custom operations in properties)
  (def user-schema
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  ;; Parse the schema
  (def parsed (parser/parse-schema :User user-schema))

  ;; Define custom operations separately
  (def custom-operations
    {:queries {:searchUsers {:args [:map [:query :string]]
                             :return-type :User
                             :description "Search users by query string"}}})

  ;; Generate Lacinia schema with custom operations
  (def lacinia-schema (generate-schemas [parsed] custom-operations))

  ;; Inspect the result
  (:queries lacinia-schema)
  ;; => {:user {...}           ; auto-generated from :ringline/query-root
  ;;     :searchUsers {...}}   ; custom query

  ;; ============================================================================
  ;; Example 2: Custom Query and Mutation
  ;; ============================================================================

  (def custom-ops-both
    {:queries {:searchUsers {:args [:map [:query :string] [:limit {:optional true} :int]]
                             :return-type :User
                             :description "Search users"}}
     :mutations {:banUser {:args [:map [:user-id :uuid]]
                           :return-type :User
                           :description "Ban a user"}}})

  (def schema-with-both (generate-schemas [parsed] custom-ops-both))

  (:queries schema-with-both)
  ;; => {:user {...}, :searchUsers {...}}

  (:mutations schema-with-both)
  ;; => {:banUser {...}}

  ;; ============================================================================
  ;; Example 3: Conflict Resolution (Custom Overrides Auto-Generated)
  ;; ============================================================================

  ;; Define custom operation with same name as auto-generated
  (def custom-override
    {:queries {:user {:args [:map [:id :uuid]]
                      :return-type :User
                      :description "Custom user lookup (overrides auto-generated)"}}})

  (def schema-override (generate-schemas [parsed] custom-override))

  ;; Only one :user query exists (custom overrode auto-generated)
  (count (:queries schema-override))
  ;; => 1

  (get-in schema-override [:queries :user :description])
  ;; => "Custom user lookup (overrides auto-generated)"

  ;; ============================================================================
  ;; Example 4: Multiple Entities with Custom Operations
  ;; ============================================================================

  (def order-schema
    [:map {:ringline/datomic-ns :order
           :ringline/query-root true}
     [:id :uuid]
     [:status :string]
     [:total :int]])

  (def parsed-user (parser/parse-schema :User user-schema))
  (def parsed-order (parser/parse-schema :Order order-schema))

  (def multi-custom-ops
    {:queries {:searchUsers {:args [:map [:query :string]]
                             :return-type :User}}
     :mutations {:approveOrder {:args [:map [:order-id :uuid]]
                                :return-type :Order}}})

  (def multi-schema (generate-schemas [parsed-user parsed-order] multi-custom-ops))

  (:queries multi-schema)
  ;; => {:user {...}          ; auto-generated from User
  ;;     :order {...}         ; auto-generated from Order
  ;;     :searchUsers {...}}  ; custom

  (:mutations multi-schema)
  ;; => {:approveOrder {...}} ; custom

  ;; ============================================================================
  ;; Example 5: Attaching Resolvers
  ;; ============================================================================

  (defn search-users-fn [ctx args value]
    ;; Mock implementation
    [{:id "user-1" :username "alice" :email "alice@example.com"}
     {:id "user-2" :username "bob" :email "bob@example.com"}])

  (defn ban-user-fn [ctx args value]
    {:id (:user-id args)
     :username "banned"
     :email "banned@example.com"})

  (def resolvers
    {:searchUsers search-users-fn
     :banUser ban-user-fn})

  (def schema-with-resolvers
    (attach-resolvers schema-with-both resolvers))

  ;; Verify resolvers attached
  (fn? (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
  ;; => true

  (not= :custom-resolver-placeholder
        (get-in schema-with-resolvers [:queries :searchUsers :resolve]))
  ;; => true
  )
