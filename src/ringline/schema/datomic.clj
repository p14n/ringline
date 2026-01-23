(ns ringline.schema.datomic
  "Generate Datomic schemas from parsed Malli schemas"
  (:require [malli.core :as m]
            [ringline.schema.types :as types]
            [ringline.schema.properties :as props]))

;; Malli schemas for validation

(def DatomicAttribute
  "Schema for a Datomic attribute definition"
  [:map
   [:db/ident :keyword]
   [:db/valueType :keyword]
   [:db/cardinality :keyword]
   [:db/doc {:optional true} :string]])

(def DatomicSchema
  "Schema for a complete Datomic schema"
  [:map
   [:source-entity :keyword]
   [:attributes [:vector DatomicAttribute]]])

;; Type and cardinality mapping

(defn- field->datomic-type
  "Convert a field's Malli type to Datomic valueType"
  [field]
  (types/malli-type->datomic-type (:type field)))

(defn- field->datomic-cardinality
  "Convert a field's cardinality to Datomic cardinality keyword"
  [field]
  (case (:cardinality field)
    :one :db.cardinality/one
    :many :db.cardinality/many
    :db.cardinality/one))

;; Namespace handling

(defn- get-entity-namespace
  "Get the Datomic namespace for an entity from its properties"
  [parsed-schema]
  (or (props/get-datomic-ns (:properties parsed-schema))
      ;; Fall back to schema name
      (:schema-name parsed-schema)))

(defn- apply-namespace
  "Apply namespace to a field name"
  [field-name entity-namespace]
  (keyword (name entity-namespace) (name field-name)))

;; Attribute generation

(defn- field->attribute
  "Convert a parsed field to a Datomic attribute definition"
  [field entity-namespace]
  (let [datomic-type (field->datomic-type field)
        cardinality (field->datomic-cardinality field)
        ident (apply-namespace (:name field) entity-namespace)]
    (cond-> {:db/ident ident
             :db/valueType datomic-type
             :db/cardinality cardinality}
      ;; Add doc if field has properties with documentation
      (get-in field [:properties :doc])
      (assoc :db/doc (get-in field [:properties :doc])))))

(defn- fields->attributes
  "Convert all fields to Datomic attributes"
  [fields entity-namespace]
  (mapv #(field->attribute % entity-namespace) fields))

;; Main generation functions

(defn generate-schema
  "Generate Datomic schema from a parsed Malli schema.
   
   Args:
     parsed-schema - ParsedSchema map from parser
   
   Returns:
     DatomicSchema map with :source-entity and :attributes"
  [parsed-schema]
  (let [entity-namespace (get-entity-namespace parsed-schema)
        attributes (fields->attributes (:fields parsed-schema) entity-namespace)
        result {:source-entity (:schema-name parsed-schema)
                :attributes attributes}]
    ;; Validate the result
    (when-not (m/validate DatomicSchema result)
      (throw (ex-info "Invalid DatomicSchema"
                      {:source-entity (:schema-name parsed-schema)
                       :errors (m/explain DatomicSchema result)})))
    result))

(defn generate-schemas
  "Generate Datomic schemas for multiple parsed entities.
   
   Args:
     parsed-schemas - Vector of ParsedSchema maps
   
   Returns:
     Vector of DatomicSchema maps"
  [parsed-schemas]
  (mapv generate-schema parsed-schemas))

(defn schema->transaction
  "Convert DatomicSchema to transaction data ready for Datomic transact.
   
   Args:
     datomic-schema - DatomicSchema map
   
   Returns:
     Vector of attribute maps ready for (d/transact conn {:tx-data ...})"
  [datomic-schema]
  (:attributes datomic-schema))

