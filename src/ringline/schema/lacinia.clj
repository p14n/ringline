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
  "Generate Lacinia query schema from CustomQueryDefinition.

   Converts custom query definition to Lacinia query schema format.
   Returns map with query name as key and Lacinia query spec as value.

   Args:
     custom-query - CustomQueryDefinition map

   Returns:
     Map with query name as key, e.g., {:searchUsers {:type :User :args {...} :resolve ...}}"
  [custom-query]
  (let [query-name (:name custom-query)
        return-type (resolve-type-reference (:return-type custom-query))
        args (malli-args->lacinia-args (:args custom-query))
        description (:description custom-query)]
    {query-name (cond-> {:type return-type
                         :args args
                         :resolve :custom-resolver-placeholder}
                  description (assoc :description description))}))

(defn generate-custom-mutation-schema
  "Generate Lacinia mutation schema from CustomMutationDefinition.

   Converts custom mutation definition to Lacinia mutation schema format.
   Returns map with mutation name as key and Lacinia mutation spec as value.

   Args:
     custom-mutation - CustomMutationDefinition map

   Returns:
     Map with mutation name as key, e.g., {:approveOrder {:type :Order :args {...} :resolve ...}}"
  [custom-mutation]
  (let [mutation-name (:name custom-mutation)
        return-type (resolve-type-reference (:return-type custom-mutation))
        args (malli-args->lacinia-args (:args custom-mutation))
        description (:description custom-mutation)]
    {mutation-name (cond-> {:type return-type
                            :args args
                            :resolve :custom-resolver-placeholder}
                     description (assoc :description description))}))

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
                 :args args
                 :resolve (keyword (str "resolve-" (name query-name)))}}))

;; Main generation functions

(defn generate-schema
  "Generate Lacinia GraphQL schema from a parsed Malli schema.

   Extended to process custom queries and mutations from ParsedSchema.
   Merges custom operations with auto-generated operations.
   Custom operations override auto-generated on name conflicts.

   Args:
     parsed-schema - ParsedSchema map from parser

   Returns:
     LaciniaSchema map with :objects and optionally :queries/:mutations"
  [parsed-schema]
  (let [entity-name (:schema-name parsed-schema)
        graphql-name (keyword->graphql-name entity-name)
        object-type (generate-object-type parsed-schema)
        is-query-root? (props/query-root? (:properties parsed-schema))

        ;; Generate auto-generated queries
        auto-queries (when is-query-root?
                       (generate-query-for-entity parsed-schema))

        ;; Generate custom queries
        custom-query-schema (when-let [custom-query (:custom-query parsed-schema)]
                              (generate-custom-query-schema custom-query))

        ;; Generate custom mutations
        custom-mutation-schema (when-let [custom-mutation (:custom-mutation parsed-schema)]
                                 (generate-custom-mutation-schema custom-mutation))

        ;; Merge queries (custom overrides auto-generated)
        all-queries (merge auto-queries custom-query-schema)

        result (cond-> {:objects {graphql-name object-type}}
                 (seq all-queries)
                 (assoc :queries all-queries)

                 (seq custom-mutation-schema)
                 (assoc :mutations custom-mutation-schema))]
    ;; Validate the result
    (when-not (m/validate LaciniaSchema result)
      (throw (ex-info "Invalid LaciniaSchema"
                      {:entity-name entity-name
                       :errors (m/explain LaciniaSchema result)})))
    result))

(defn generate-schemas
  "Generate complete Lacinia GraphQL schema from multiple parsed entities.

   Extended to merge custom queries and mutations from all entities.

   Args:
     parsed-schemas - Vector of ParsedSchema maps

   Returns:
     Single LaciniaSchema map with all objects, queries, mutations, and custom scalars merged"
  [parsed-schemas]
  (let [individual-schemas (map generate-schema parsed-schemas)
        merged {:objects (apply merge {} (map :objects individual-schemas))
                :queries (apply merge {} (map :queries individual-schemas))
                :mutations (apply merge {} (map :mutations individual-schemas))
                :scalars (custom-scalars)}]  ; T030: Add custom scalars
    ;; Validate the result
    (when-not (m/validate LaciniaSchema merged)
      (throw (ex-info "Invalid merged LaciniaSchema"
                      {:errors (m/explain LaciniaSchema merged)})))
    merged))

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
;; Rich Comment Block - REPL Examples
;; ============================================================================

(comment
  (require '[ringline.schema.parser :as parser])

  ;; Example 1: Generate schema with custom query
  (def user-schema
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true
           :ringline/searchable-fields [:username]
           :ringline/custom-query {:name :searchUsers
                                   :args [:map [:query :string] [:limit {:optional true} :int]]
                                   :return-type :User
                                   :description "Search users by query string"}}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  (def parsed-user (parser/parse-schema :User user-schema))
  (def lacinia-schema (generate-schema parsed-user))

  ;; Verify both auto-generated and custom queries exist
  (:queries lacinia-schema)
  ;; => {:user {...} :searchUsers {...}}

  (get-in lacinia-schema [:queries :searchUsers])
  ;; => {:type :User
  ;;     :args {:query {:type String}
  ;;            :limit {:type Int :optional true}}
  ;;     :resolve :custom-resolver-placeholder
  ;;     :description "Search users by query string"}

  ;; Example 2: Generate schema with custom mutation
  (def order-schema
    [:map {:ringline/datomic-ns :order
           :ringline/custom-mutation {:name :approveOrder
                                      :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                                      :return-type :Order
                                      :description "Approve an order"}}
     [:id :uuid]
     [:status :string]])

  (def parsed-order (parser/parse-schema :Order order-schema))
  (def order-lacinia (generate-schema parsed-order))

  (:mutations order-lacinia)
  ;; => {:approveOrder {...}}

  (get-in order-lacinia [:mutations :approveOrder])
  ;; => {:type :Order
  ;;     :args {:order-id {:type ID}
  ;;            :notes {:type String :optional true}}
  ;;     :resolve :custom-resolver-placeholder
  ;;     :description "Approve an order"}

  ;; Example 3: Attach custom resolvers
  (def search-users-resolver
    (fn [context args value]
      ;; Mock implementation
      [{:id "1" :username "alice" :email "alice@example.com"}
       {:id "2" :username "bob" :email "bob@example.com"}]))

  (def approve-order-resolver
    (fn [context args value]
      ;; Mock implementation
      {:id (:order-id args)
       :status "approved"}))

  (def resolvers
    {:searchUsers search-users-resolver
     :approveOrder approve-order-resolver})

  (def user-with-resolvers (attach-resolvers lacinia-schema resolvers))
  (def order-with-resolvers (attach-resolvers order-lacinia resolvers))

  ;; Verify resolvers attached
  (fn? (get-in user-with-resolvers [:queries :searchUsers :resolve]))
  ;; => true

  (fn? (get-in order-with-resolvers [:mutations :approveOrder :resolve]))
  ;; => true

  ;; Example 4: Conflict resolution - custom overrides auto-generated
  (def user-with-conflict
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true
           :ringline/searchable-fields [:username]
           :ringline/custom-query {:name :user  ; Same name as auto-generated
                                   :args [:map [:id :uuid]]
                                   :return-type :User
                                   :description "Custom user lookup"}}
     [:id :uuid]
     [:username :string]])

  (def parsed-conflict (parser/parse-schema :User user-with-conflict))
  (def conflict-schema (generate-schema parsed-conflict))

  ;; Only one :user query exists (custom overrode auto-generated)
  (count (:queries conflict-schema))
  ;; => 1

  (get-in conflict-schema [:queries :user :description])
  ;; => "Custom user lookup"  (proves custom overrode auto-generated)

  ;; Example 5: Generate merged schema from multiple entities
  (def schemas
    {:User user-schema
     :Order order-schema})

  (def parsed-schemas (parser/parse-schemas schemas))
  (def merged-schema (generate-schemas parsed-schemas))

  ;; Verify all operations merged
  (keys (:queries merged-schema))
  ;; => (:user :searchUsers)

  (keys (:mutations merged-schema))
  ;; => (:approveOrder)

  (keys (:objects merged-schema))
  ;; => (:User :Order)

  :end)

