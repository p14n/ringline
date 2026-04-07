(ns ringline.schema.parser
  "Parse Malli schemas and extract entity metadata"
  (:require [malli.core :as m]
            [ringline.schema.types :as types]
            [ringline.schema.properties :as props]))

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
  [field-type]
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
    (cond (types/collection-type? field-type)
          (let [children (m/children field-schema)]
            (when (seq children)
              (extract-ref-type (first children))))
          (= :ref field-type)
          (let [children (m/children field-schema)]
            (when (seq children)
              (extract-ref-type (first children))))
          :else field-type)))

(defn resolve-if-schema-var [s]
  (let [t (m/type s)]
    (cond (and (= t :malli.core/schema)
               (var? (m/form s)))
          (deref (m/form s))

          (some #{:ref :vector} [t])
          (let [children (m/children s)]
            (when (seq children)
              (resolve-if-schema-var (first children))))
          :else nil)))

(defn field-type-from-schema
  [schema field]
  (->> schema
       (filter vector?)
       (map (juxt first last))
       (into {})
       field))

(defn field-type-from-schema-var
  [schema-var field]
  (-> (deref schema-var)
      (field-type-from-schema field)))


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
        cardinality (extract-cardinality field-type)
        field-props (or field-properties (m/properties schema-obj))
        ref-var (resolve-if-schema-var schema-obj)
        ref-id-field-type (some-> ref-var (field-type-from-schema :id))
        ref-to (some-> ref-var (m/properties) :ringline/schema-name)]
    {:name field-name
     :type actual-type
     :required (not (:optional field-props))  ; Check if field is optional
     :cardinality cardinality
     :enum-values (extract-enum-values schema-obj)
     :ref-id-field-type ref-id-field-type
     :properties (if ref-to
                   (assoc field-props :ringline/ref-to ref-to)
                   field-props)}))

;; Property extraction

(defn extract-properties
  "Extract custom properties from Malli schema"
  [schema]
  (or (m/properties schema) {}))

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

(defn default-namespace
  "Generate default namespace from schema name"
  [schema-name]
  (name schema-name))

(defn add-default-namespace [schema-name schema]
  (let [props (extract-properties schema)]
    (if (props/get-datomic-ns props)
      schema
      (assoc props :ringline/datomic-ns (default-namespace schema-name)))))

;; Main parsing functions
(defn parse-schema
  "Parse a Malli schema and extract entity metadata.

   Args:
     schema-name - Keyword name for the entity (e.g., :User, :Post)
     malli-schema - Malli schema definition

   Returns:
     ParsedSchema map with :schema-name, :fields, :properties, :relationships"
  [schema-name malli-schema]
  (let [properties (extract-properties malli-schema)
        children (m/children malli-schema)
        fields (mapv parse-field children)
        relationships (extract-relationships fields schema-name)
        result {:schema-name schema-name
                :fields fields
                :properties properties
                :relationships relationships}]
    result))


(defn malli-schema->datomic-ns
  "Extract Datomic namespace from schema properties"
  [schema]
  (let [properties (m/properties schema)]
    (props/get-datomic-ns properties)))

(defn id-type [schema]
  (->> schema
       :fields
       (filter #(-> % :name (= :id)))
       (first)
       :type))

(defn is-schema-var [t]
  (and (= (m/type t) :malli.core/schema)
       (var? (m/form t))))

(defn malli-entity->malli-field [malli-entity field-kw]
  (->> malli-entity
       (filter vector?)
       (filter #(-> % first (= field-kw)))
       (first)))

(defn malli-entity->malli-field-type [malli-entity field-kw]
  (-> malli-entity
      (malli-entity->malli-field field-kw)
      (last)))

(defn unwrap-ref [ft]
  (let [s (when (= :malli.core/schema (type ft))
            (m/form ft))]
    (if (and (vector? s) (= (first s) :ref))
      (second s)
      ft)))

(defn correct-field-type [f]
  (let [unwrapped (unwrap-ref (last f))]
    (if (is-schema-var unwrapped)
      (-> (drop-last f)
          (vec)
          (conj (field-type-from-schema-var (m/form unwrapped) :id)))
      f)))


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
  (let [;field-name (name (:field relationship))
        target (:target relationship)
        ;; Try to match field name to schema name (e.g., :posts -> :post)
        potential-targets (filter #(= % target) #_(or (= field-name (name %))
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
;; Rich Comment Block - Parser Usage Examples
;; ============================================================================

(comment
  ;; The parser extracts entity metadata from Malli schemas
  ;; It does NOT parse custom queries or mutations (those are defined separately)

  ;; ============================================================================
  ;; Example 1: Basic Entity Parsing
  ;; ============================================================================

  (def user-schema
    [:map {:ringline/datomic-ns :user
           :ringline/query-root true}
     [:id :uuid]
     [:username :string]
     [:email :string]])

  (def parsed (parse-schema :User user-schema))

  parsed
  ;; => {:schema-name :User
  ;;     :fields [{:name :id :type :uuid :required true ...}
  ;;              {:name :username :type :string :required true ...}
  ;;              {:name :email :type :string :required true ...}]
  ;;     :properties {:ringline/datomic-ns :user
  ;;                  :ringline/query-root true}
  ;;     :relationships []}

  ;; ============================================================================
  ;; Example 2: Entity with Relationships
  ;; ============================================================================

  (def post-schema
    [:map {:ringline/datomic-ns :post}
     [:id :uuid]
     [:title :string]
     [:author-id {:ringline/ref-to :user} :uuid]])

  (def parsed-post (parse-schema :Post post-schema))

  (:relationships parsed-post)
  ;; => [{:field :author-id
  ;;      :source :Post
  ;;      :target :user
  ;;      :cardinality :one
  ;;      :bidirectional false}]

  ;; ============================================================================
  ;; Example 3: Multiple Schemas with Relationship Resolution
  ;; ============================================================================

  (def schemas-map
    {:User user-schema
     :Post post-schema})

  (def parsed-schemas (parse-schemas schemas-map))

  ;; Returns vector of ParsedSchema maps with resolved relationships
  (count parsed-schemas)
  ;; => 2

  ;; ============================================================================
  ;; IMPORTANT: Custom Operations Are NOT Parsed Here
  ;; ============================================================================

  ;; Custom queries and mutations are NOT parsed from entity schemas.
  ;; They are defined separately and passed to ringline.schema.lacinia/generate-schemas

  ;; The parser only extracts:
  ;; - Entity fields and their types
  ;; - Entity properties (datomic-ns, query-root, searchable-fields, etc.)
  ;; - Entity relationships (ref-to)

  ;; For custom operations, see:
  ;; - ringline.schema.lacinia (generate-schemas function)
  ;; - ringline.core (init-framework function)
  )

