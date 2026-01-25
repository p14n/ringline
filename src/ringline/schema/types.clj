(ns ringline.schema.types
  "Type mappings between Malli, Datomic, and GraphQL"
  (:require [malli.core :as m]))

;; Malli type → Datomic type mapping
(def malli->datomic
  "Map of Malli schema types to Datomic value types"
  {:string  :db.type/string
   :int     :db.type/long
   :double  :db.type/double
   :boolean :db.type/boolean
   :uuid    :db.type/uuid
   :inst    :db.type/instant
   :keyword :db.type/keyword
   :ref     :db.type/ref
   ;; Custom scalar types (T010-T013)
   :time/local-date      :db.type/instant  ; Date stored as midnight UTC
   :time/offset-date-time :db.type/instant ; DateTime stored as instant (dual-attribute for timezone)
   :enum                 :db.type/keyword  ; Enum stored as keyword
   :decimal              :db.type/bigdec}) ; Decimal stored as BigDecimal

;; Malli type → GraphQL type mapping
(def malli->graphql
  "Map of Malli schema types to GraphQL (Lacinia) types"
  {:string  'String
   :int     'Int
   :double  'Float
   :boolean 'Boolean
   :uuid    'ID
   :inst    'String  ; ISO-8601 formatted string
   :keyword 'String
   :enum    'String  ; Will be refined to specific enum type
   :vector  'list    ; Will be refined with element type
   :map     'Object  ; Will be refined to specific object type
   ;; Custom scalar types (T014-T017)
   :time/local-date      'Date     ; ISO8601 date string (YYYY-MM-DD, 10 chars)
   :time/offset-date-time 'DateTime ; ISO8601 datetime with timezone (25 chars)
   :decimal              'Decimal}) ; Decimal scalar (string to avoid JS precision loss)

;; Cardinality detection
(def cardinality-types
  "Malli types that indicate cardinality :many"
  #{:vector :sequential :set})

(defn malli-type->datomic-type
  "Convert a Malli type keyword to a Datomic type keyword"
  [malli-type]
  (get malli->datomic malli-type))

(defn malli-type->graphql-type
  "Convert a Malli type keyword to a GraphQL (Lacinia) type symbol"
  [malli-type]
  (get malli->graphql malli-type))

(defn collection-type?
  "Check if a Malli type represents a collection (cardinality :many)"
  [malli-type]
  (contains? cardinality-types malli-type))

;; ============================================================================
;; Custom Query and Mutation Definition Schemas
;; ============================================================================

(def CustomQueryDefinition
  "Schema for a custom query definition extracted from Malli schema properties.

   Contains the query name, argument schema, and return type."
  [:map
   [:name :keyword]                    ; GraphQL query name (e.g., :searchUsers)
   [:args :any]                        ; Malli schema for query arguments
   [:return-type :keyword]             ; Return type reference (e.g., :User, :string)
   [:description {:optional true} :string]])  ; Optional GraphQL description

(def CustomMutationDefinition
  "Schema for a custom mutation definition extracted from Malli schema properties.

   Contains the mutation name, input schema, and return type."
  [:map
   [:name :keyword]                    ; GraphQL mutation name (e.g., :approveOrder)
   [:args :any]                        ; Malli schema for mutation input
   [:return-type :keyword]             ; Return type reference (e.g., :Order, :boolean)
   [:description {:optional true} :string]])  ; Optional GraphQL description

(defn validate-custom-query-definition
  "Validate a custom query definition against the CustomQueryDefinition schema.
   Returns nil if valid, or explanation if invalid."
  [query-def]
  (m/explain CustomQueryDefinition query-def))

(defn validate-custom-mutation-definition
  "Validate a custom mutation definition against the CustomMutationDefinition schema.
   Returns nil if valid, or explanation if invalid."
  [mutation-def]
  (m/explain CustomMutationDefinition mutation-def))

