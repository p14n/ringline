(ns ringline.schema.parser
  "Parse Malli schemas and extract entity metadata"
  (:require [malli.core :as m]
            [ringline.schema.types :as types]
            [ringline.schema.properties :as props]))

;; Malli schemas for validation

(def FieldDefinition
  "Schema for a field definition"
  [:map
   [:name :keyword]
   [:type :keyword]
   [:required :boolean]
   [:cardinality [:enum :one :many]]
   [:enum-values [:maybe [:vector :any]]]
   [:properties [:maybe :map]]])

(def Relationship
  "Schema for a relationship definition"
  [:map
   [:field :keyword]
   [:source :keyword]
   [:target [:maybe :keyword]]
   [:cardinality [:enum :one :many]]
   [:bidirectional :boolean]])

(def ParsedSchema
  "Schema for a parsed Malli schema"
  [:map
   [:schema-name :keyword]
   [:fields [:vector FieldDefinition]]
   [:properties :map]
   [:relationships [:vector Relationship]]])

;; Field extraction

(defn- extract-field-type
  "Extract the Malli type from a field schema (expects Malli schema object)"
  [field-schema]
  (let [schema-type (m/type field-schema)]
    ;; If it's a schema reference (:malli.core/schema), dereference it
    (if (= schema-type :malli.core/schema)
      ;; Get the form and extract the type from it
      (let [form (m/form field-schema)]
        (if (keyword? form)
          form  ; The form is the type keyword (e.g., :time/local-date)
          schema-type))
      schema-type)))

(defn- extract-cardinality
  "Determine cardinality (:one or :many) from field type"
  [field-type field-schema]
  (if (types/collection-type? field-type)
    :many
    :one))

(defn- extract-enum-values
  "Extract enum values if field is an enum type"
  [field-schema]
  (when (= :enum (m/type field-schema))
    (vec (m/children field-schema))))

(defn- extract-ref-type
  "Extract the actual type from a ref field (handles vectors of refs)"
  [field-schema]
  (let [field-type (m/type field-schema)]
    (if (types/collection-type? field-type)
      ;; For [:vector :uuid], get the inner type
      ;; Note: m/children returns Malli schema objects, not raw keywords
      (let [children (m/children field-schema)]
        (when (seq children)
          (m/type (first children))))
      ;; For direct types
      field-type)))

(defn- parse-field
  "Parse a single field from Malli schema children"
  [[field-name field-properties field-schema]]
  ;; Convert raw vector/keyword to Malli schema object if needed
  (let [schema-obj (if (or (vector? field-schema) (keyword? field-schema))
                     (m/schema field-schema)
                     field-schema)
        field-type (extract-field-type schema-obj)
        actual-type (if (types/collection-type? field-type)
                      (extract-ref-type schema-obj)
                      field-type)
        cardinality (extract-cardinality field-type schema-obj)
        field-props (or field-properties (m/properties schema-obj))]
    {:name field-name
     :type actual-type
     :required (not (:optional field-props))  ; Check if field is optional
     :cardinality cardinality
     :enum-values (extract-enum-values schema-obj)
     :properties field-props}))

;; Property extraction

(defn- extract-properties
  "Extract custom properties from Malli schema"
  [schema]
  (or (m/properties schema) {}))

;; Custom query/mutation extraction

(defn parse-custom-query
  "Parse custom query definition from schema properties.

   Extracts and validates :ringline/custom-query property.
   Returns CustomQueryDefinition map or nil if not present.

   Args:
     schema-name - Keyword name for the entity (e.g., :User)
     malli-schema - Malli schema definition

   Returns:
     CustomQueryDefinition map or nil

   Throws:
     ex-info if custom query definition is invalid"
  [schema-name malli-schema]
  (let [properties (extract-properties malli-schema)
        custom-query (props/get-custom-query properties)]
    (when custom-query
      ;; Validate the custom query definition
      (when-let [explanation (types/validate-custom-query-definition custom-query)]
        (throw (ex-info "Invalid custom query definition"
                        {:schema-name schema-name
                         :custom-query custom-query
                         :errors explanation})))
      custom-query)))

(defn parse-custom-mutation
  "Parse custom mutation definition from schema properties.

   Extracts and validates :ringline/custom-mutation property.
   Returns CustomMutationDefinition map or nil if not present.

   Args:
     schema-name - Keyword name for the entity (e.g., :User)
     malli-schema - Malli schema definition

   Returns:
     CustomMutationDefinition map or nil

   Throws:
     ex-info if custom mutation definition is invalid"
  [schema-name malli-schema]
  (let [properties (extract-properties malli-schema)
        custom-mutation (props/get-custom-mutation properties)]
    (when custom-mutation
      ;; Validate the custom mutation definition
      (when-let [explanation (types/validate-custom-mutation-definition custom-mutation)]
        (throw (ex-info "Invalid custom mutation definition"
                        {:schema-name schema-name
                         :custom-mutation custom-mutation
                         :errors explanation})))
      custom-mutation)))

;; Relationship detection

(defn- field->relationship
  "Convert a ref field to a relationship definition.
   A field is considered a relationship if it has the :ringline/ref-to property."
  [field schema-name]
  (when-let [ref-target (props/get-ref-target (:properties field))]
    {:field (:name field)
     :source schema-name
     :target ref-target  ; Target entity from :ringline/ref-to property
     :cardinality (:cardinality field)
     :bidirectional false}))

(defn- extract-relationships
  "Extract all relationships from parsed fields"
  [fields schema-name]
  (->> fields
       (map #(field->relationship % schema-name))
       (filter some?)
       vec))

;; Main parsing functions

(defn parse-schema
  "Parse a Malli schema and extract entity metadata.

   Args:
     schema-name - Keyword name for the entity (e.g., :User, :Post)
     malli-schema - Malli schema definition

   Returns:
     ParsedSchema map with :schema-name, :fields, :properties, :relationships,
     :custom-query (optional), :custom-mutation (optional)"
  [schema-name malli-schema]
  (let [properties (extract-properties malli-schema)
        children (m/children malli-schema)
        fields (mapv parse-field children)
        relationships (extract-relationships fields schema-name)
        custom-query (parse-custom-query schema-name malli-schema)
        custom-mutation (parse-custom-mutation schema-name malli-schema)
        result {:schema-name schema-name
                :fields fields
                :properties properties
                :relationships relationships
                :custom-query custom-query
                :custom-mutation custom-mutation}]
    ;; Note: We don't validate against ParsedSchema here because we've extended it
    ;; with :custom-query and :custom-mutation fields which aren't in the original schema
    result))

;; Rich comment block with REPL examples
(comment
  ;; Example: Parse a single Malli schema
  (require '[ringline.schema.parser :as parser])
  (require '[malli.core :as m])

  (def user-schema
    [:map {:ringline/datomic-ns "user"
           :ringline/query-root true
           :ringline/searchable [:email]}
     [:id :uuid]
     [:email :string]
     [:username :string]
     [:posts [:vector :ref]]])

  (def parsed (parser/parse-schema :user user-schema))

  ;; Inspect parsed result
  (:schema-name parsed)     ; => :user
  (:fields parsed)          ; => Vector of field definitions
  (:properties parsed)      ; => {:ringline/datomic-ns "user" ...}
  (:relationships parsed)   ; => Vector of relationships

  ;; Example: Parse multiple schemas with relationships
  (def post-schema
    [:map {:ringline/datomic-ns "post"}
     [:id :uuid]
     [:title :string]
     [:author :ref]])

  (def schemas {:user user-schema :post post-schema})
  (def parsed-all (parser/parse-schemas schemas))

  ;; Inspect relationships
  (mapcat :relationships parsed-all)
  ; => [{:field :posts :source :user :target :post :cardinality :many ...}
  ;     {:field :author :source :post :target :user :cardinality :one ...}]

  )

(defn- resolve-relationship-target
  "Resolve the target entity for a relationship based on field name"
  [relationship schema-names]
  (let [field-name (name (:field relationship))
        ;; Try to match field name to schema name (e.g., :posts -> :post)
        potential-targets (filter #(or (= field-name (name %))
                                       (= field-name (str (name %) "s"))
                                       (= (str field-name "s") (name %)))
                                  schema-names)]
    (if (seq potential-targets)
      (assoc relationship :target (first potential-targets))
      relationship)))

(defn- resolve-relationships
  "Resolve relationship targets across all parsed schemas"
  [parsed-schemas]
  (let [schema-names (map :schema-name parsed-schemas)]
    (mapv (fn [schema]
            (update schema :relationships
                    (fn [rels]
                      (mapv #(resolve-relationship-target % schema-names) rels))))
          parsed-schemas)))

(defn parse-schemas
  "Parse multiple Malli schemas and resolve relationships.
   
   Args:
     schemas-map - Map of schema-name to malli-schema (e.g., {:User user-schema :Post post-schema})
   
   Returns:
     Vector of ParsedSchema maps with resolved relationships"
  [schemas-map]
  (let [parsed (mapv (fn [[schema-name malli-schema]]
                       (parse-schema schema-name malli-schema))
                     schemas-map)]
    (resolve-relationships parsed)))

;; ============================================================================
;; Rich Comment Block - REPL Examples
;; ============================================================================

(comment
  (require '[malli.core :as m])

  ;; Example 1: Parse schema with custom query
  (def user-schema
    [:map {:ringline/datomic-ns :user
           :ringline/custom-query {:name :searchUsers
                                   :args [:map [:query :string] [:limit {:optional true} :int]]
                                   :return-type :User
                                   :description "Search users by query string"}}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  (parse-custom-query :User user-schema)
  ;; => {:name :searchUsers
  ;;     :args [:map [:query :string] [:limit {:optional true} :int]]
  ;;     :return-type :User
  ;;     :description "Search users by query string"}

  (def parsed-user (parse-schema :User user-schema))
  (:custom-query parsed-user)
  ;; => {:name :searchUsers ...}

  ;; Example 2: Parse schema with custom mutation
  (def order-schema
    [:map {:ringline/datomic-ns :order
           :ringline/custom-mutation {:name :approveOrder
                                      :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
                                      :return-type :Order
                                      :description "Approve an order"}}
     [:id :uuid]
     [:status :string]])

  (parse-custom-mutation :Order order-schema)
  ;; => {:name :approveOrder
  ;;     :args [:map [:order-id :uuid] [:notes {:optional true} :string]]
  ;;     :return-type :Order
  ;;     :description "Approve an order"}

  (def parsed-order (parse-schema :Order order-schema))
  (:custom-mutation parsed-order)
  ;; => {:name :approveOrder ...}

  ;; Example 3: Invalid custom query throws exception
  (def invalid-schema
    [:map {:ringline/custom-query {:args [:map [:query :string]]}}  ; Missing :name and :return-type
     [:id :uuid]])

  (try
    (parse-custom-query :User invalid-schema)
    (catch Exception e
      (ex-data e)))
  ;; => {:schema-name :User
  ;;     :custom-query {...}
  ;;     :errors ...}

  ;; Example 4: Parse multiple schemas with custom operations
  (def schemas
    {:User user-schema
     :Order order-schema})

  (def parsed-schemas (parse-schemas schemas))
  (count parsed-schemas)
  ;; => 2

  (map :schema-name parsed-schemas)
  ;; => (:User :Order)

  (map :custom-query parsed-schemas)
  ;; => ({:name :searchUsers ...} nil)

  (map :custom-mutation parsed-schemas)
  ;; => (nil {:name :approveOrder ...})

  :end)

