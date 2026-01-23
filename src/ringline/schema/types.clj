(ns ringline.schema.types
  "Type mappings between Malli, Datomic, and GraphQL")

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
   :ref     :db.type/ref})

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
   :map     'Object}) ; Will be refined to specific object type

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

