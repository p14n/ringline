(ns ringline.response.transformer
  "Transform Datomic query results to Lacinia-compatible GraphQL format"
  (:require [malli.core :as m]
            [clojure.string :as str]
            [ringline.schema.scalars :as scalars])
  (:import [java.time Instant LocalDate ZoneOffset]))

;; Helper functions

(defn- namespaced-keyword->simple
  "Convert a namespaced keyword to a simple keyword (e.g., :user/id -> :id)"
  [kw]
  (if (namespace kw)
    (keyword (name kw))
    kw))

(defn- get-entity-namespace
  "Get the entity namespace from ParsedSchema"
  [parsed-schema]
  (let [schema-name (:schema-name parsed-schema)]
    (str/lower-case (name schema-name))))

(defn- field-name->namespaced
  "Convert a simple field name to namespaced keyword using entity namespace"
  [entity-ns field-name]
  (keyword entity-ns (name field-name)))

;; Forward declaration for mutual recursion
(declare transform-field-value)

(defn- transform-entity
  "Transform a single Datomic entity to GraphQL format"
  [datomic-entity parsed-schema]
  (into {}
        (map (fn [[k v]]
               [(namespaced-keyword->simple k)
                (transform-field-value v parsed-schema)])
             datomic-entity)))

(defn- transform-field-value
  "Transform a field value, handling nested entities and UUID conversion.

  T033, T053, T073, T094: Extended to handle Date, DateTime, Enum, and Decimal scalar serialization"
  [value parsed-schema]
  (cond
    ;; Vector of entities (many relationship)
    (and (vector? value) (every? map? value))
    (mapv #(transform-entity % parsed-schema) value)

    ;; Single entity (one relationship)
    (and (map? value) (some namespace (keys value)))
    (transform-entity value parsed-schema)

    ;; UUID - convert to string for GraphQL ID type
    (instance? java.util.UUID value)
    (str value)

    ;; T033, T053: Instant - convert to Date or DateTime string
    ;; Heuristic: Midnight UTC = Date field, otherwise = DateTime field
    ;; Note: For proper handling, we should check field type from schema
    ;; TODO: In future, pass field type info to avoid heuristic
    (instance? Instant value)
    (let [odt (.atOffset ^Instant value ZoneOffset/UTC)]
      (if (and (= 0 (.getHour odt))
               (= 0 (.getMinute odt))
               (= 0 (.getSecond odt)))
        ;; Midnight UTC - likely a Date field
        (scalars/serialize-date (.toLocalDate odt))
        ;; Not midnight - likely a DateTime field
        ;; T053: Serialize as DateTime with UTC timezone
        (scalars/serialize-datetime odt)))

    ;; T073: Keyword - serialize to string for GraphQL enum
    ;; Enum values are stored as keywords in Datomic
    (keyword? value)
    (scalars/serialize-enum value)

    ;; T094: BigDecimal - serialize to string for GraphQL Decimal scalar
    ;; Serialized as string to avoid JavaScript precision loss (per FR-019)
    (instance? java.math.BigDecimal value)
    (scalars/serialize-decimal value)

    ;; Primitive value
    :else value))

;; Main transformation functions

(defn datomic->graphql
  "Transform Datomic entity result to GraphQL-compatible format.
   
   Args:
     datomic-entity - Datomic entity map with namespaced keywords
     parsed-schema - ParsedSchema map for the entity type
   
   Returns:
     Map with simple keywords suitable for GraphQL response"
  [datomic-entity parsed-schema]
  (transform-entity datomic-entity parsed-schema))

(defn entities->graphql
  "Transform multiple Datomic entities to GraphQL format.
   
   Args:
     datomic-entities - Vector of Datomic entity maps
     parsed-schema - ParsedSchema map for the entity type
   
   Returns:
     Vector of transformed maps"
  [datomic-entities parsed-schema]
  (mapv #(datomic->graphql % parsed-schema) datomic-entities))

(defn- transform-value
  "Transform a value for GraphQL (e.g., UUID to string).

  For custom scalar types (Date, DateTime, Decimal), pass through the raw value
  and let Lacinia's scalar serializers handle the conversion.

  For enums (keywords), convert to strings since they're mapped to String type in GraphQL."
  [value]
  (cond
    ;; UUID - convert to string for GraphQL ID type
    (instance? java.util.UUID value)
    (str value)

    ;; Keyword (enum) - convert to string
    ;; Enums are mapped to String type in GraphQL, not custom scalars
    (keyword? value)
    (name value)

    ;; Other values pass through (including java.util.Date, BigDecimal)
    ;; Lacinia's custom scalar serializers will handle these
    :else value))

(defn- filter-by-selections
  "Filter entity fields to only include selected fields"
  [entity selections nested-queries entity-ns]
  (let [selected-fields (set selections)]
    (into {}
          (keep (fn [field-name]
                  (let [namespaced-field (field-name->namespaced entity-ns field-name)
                        simple-field (namespaced-keyword->simple namespaced-field)]
                    (when (contains? entity namespaced-field)
                      (let [value (get entity namespaced-field)]
                        ;; Handle nested queries
                        (if-let [nested-query (get nested-queries field-name)]
                          (let [nested-selections (:selections nested-query)
                                nested-nested (:nested-queries nested-query)
                                ;; Infer target entity namespace from field name
                                target-ns (str/replace (name field-name) #"s$" "")
                                transformed-value (cond
                                                    (vector? value)
                                                    (mapv #(filter-by-selections % nested-selections nested-nested target-ns) value)

                                                    (map? value)
                                                    (filter-by-selections value nested-selections nested-nested target-ns)

                                                    :else (transform-value value))]
                            [simple-field transformed-value])
                          ;; Regular field - transform value
                          [simple-field (transform-value value)])))))
                selections))))

(defn transform-with-selections
  "Transform entity including only requested GraphQL selections.
   
   Args:
     datomic-entity - Datomic entity map with namespaced keywords
     query-context - QueryContext map with :selections and :nested-queries
   
   Returns:
     Map with only selected fields, using simple keywords"
  [datomic-entity query-context]
  (let [entity-type (:entity-type query-context)
        entity-ns (str/lower-case (name entity-type))
        selections (:selections query-context)
        nested-queries (:nested-queries query-context)]
    (filter-by-selections datomic-entity selections nested-queries entity-ns)))

