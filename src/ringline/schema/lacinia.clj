(ns ringline.schema.lacinia
  "Generate Lacinia GraphQL schemas from parsed Malli schemas"
  (:require [malli.core :as m]
            [ringline.schema.types :as types]
            [ringline.schema.properties :as props]
            [clojure.string :as str]))

;; Malli schemas for validation

(def LaciniaSchema
  "Schema for a Lacinia GraphQL schema"
  [:map
   [:objects :map]
   [:queries {:optional true} :map]
   [:enums {:optional true} :map]])

;; Type mapping

(defn- field->graphql-type
  "Convert a field's Malli type to GraphQL (Lacinia) type"
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
  "Convert all fields to Lacinia field definitions"
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
   
   Args:
     parsed-schema - ParsedSchema map from parser
   
   Returns:
     LaciniaSchema map with :objects and optionally :queries"
  [parsed-schema]
  (let [entity-name (:schema-name parsed-schema)
        graphql-name (keyword->graphql-name entity-name)
        object-type (generate-object-type parsed-schema)
        is-query-root? (props/query-root? (:properties parsed-schema))
        result (cond-> {:objects {graphql-name object-type}}
                 is-query-root?
                 (assoc :queries (generate-query-for-entity parsed-schema)))]
    ;; Validate the result
    (when-not (m/validate LaciniaSchema result)
      (throw (ex-info "Invalid LaciniaSchema"
                      {:entity-name entity-name
                       :errors (m/explain LaciniaSchema result)})))
    result))

(defn generate-schemas
  "Generate complete Lacinia GraphQL schema from multiple parsed entities.

   Args:
     parsed-schemas - Vector of ParsedSchema maps

   Returns:
     Single LaciniaSchema map with all objects and queries merged"
  [parsed-schemas]
  (let [individual-schemas (map generate-schema parsed-schemas)
        merged {:objects (apply merge {} (map :objects individual-schemas))
                :queries (apply merge {} (map :queries individual-schemas))}]
    ;; Validate the result
    (when-not (m/validate LaciniaSchema merged)
      (throw (ex-info "Invalid merged LaciniaSchema"
                      {:errors (m/explain LaciniaSchema merged)})))
    merged))

(defn attach-resolvers
  "Attach resolver functions to a Lacinia schema.
   
   Args:
     lacinia-schema - LaciniaSchema map
     resolvers-map - Map of query-name to resolver function
   
   Returns:
     LaciniaSchema with resolvers attached to queries"
  [lacinia-schema resolvers-map]
  (update lacinia-schema :queries
          (fn [queries]
            (into {}
                  (map (fn [[query-name query-def]]
                         (if-let [resolver (get resolvers-map query-name)]
                           [query-name (assoc query-def :resolve resolver)]
                           [query-name query-def]))
                       queries)))))

