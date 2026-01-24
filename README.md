# Ringline: Malli-GraphQL Framework

A Clojure framework that uses Malli schemas as a single source of truth to automatically generate both Datomic database schemas and Lacinia GraphQL schemas, with automatic query conversion and response transformation.

## Purpose

**Define your data model once, use it everywhere.**

Ringline eliminates the need to maintain separate schema definitions for your database and API layer. Define your entities and relationships using Malli schemas with custom properties, and Ringline automatically:

- ✅ Generates Datomic database schemas with proper attributes, types, and cardinality
- ✅ Generates Lacinia GraphQL schemas with object types, fields, and queries
- ✅ Converts GraphQL queries to Datomic pull patterns
- ✅ Transforms Datomic responses to GraphQL format

## Features

- **Single Source of Truth**: Define data models once in Malli
- **Type Safety**: Malli validation ensures schema correctness
- **Relationship Handling**: Automatic detection and conversion of entity relationships
- **Query Conversion**: GraphQL queries → Datomic pull patterns
- **Response Transformation**: Datomic entities → GraphQL responses
- **Field Filtering**: Only selected fields are included in responses
- **Search Arguments**: Automatic query argument generation from schema properties

## Installation

Add to your `deps.edn`:

```clojure
{:deps {ringline {:local/root "."}
        org.clojure/clojure {:mvn/version "1.12.0"}
        metosin/malli {:mvn/version "0.20.0"}
        com.walmartlabs/lacinia {:mvn/version "1.3.0-beta-1"}
        com.datomic/datomic-free {:mvn/version "0.9.5697"}}}
```

## Quick Start

### 1. Define Your Data Model

```clojure
(require '[malli.core :as m])

;; Define User entity
(def user-schema
  [:map
   {:ringline/datomic-ns :user
    :ringline/query-root true
    :ringline/searchable [:email :username]}
   [:id :uuid]
   [:username :string]
   [:email :string]
   [:created-at :int]
   [:posts {:ringline/ref-to :post} [:vector :uuid]]])  ; One-to-many relationship

;; Define Post entity
(def post-schema
  [:map
   {:ringline/datomic-ns :post
    :ringline/query-root true
    :ringline/searchable [:title]}
   [:id :uuid]
   [:title :string]
   [:content :string]
   [:published? :boolean]
   [:created-at :int]
   [:author {:ringline/ref-to :user} :uuid]])  ; Many-to-one relationship

(def schemas {:user user-schema :post post-schema})
```

### 2. Initialize the Framework

```clojure
(require '[ringline.core :as core])

;; Initialize framework - generates all schemas
(def framework (core/init-framework schemas {}))

;; Access generated schemas
(def datomic-schemas (:datomic framework))  ; Vector of DatomicSchema maps
(def lacinia-schema (:lacinia framework))   ; LaciniaSchema map
(def parsed-schemas (:parsed framework))    ; Vector of ParsedSchema maps
```

### 3. Set Up Datomic Database

```clojure
(require '[datomic.api :as d]
         '[ringline.schema.datomic :as datomic-gen])

;; Create database
(def db-uri "datomic:mem://example")
(d/create-database db-uri)
(def conn (d/connect db-uri))

;; Convert to transaction format and transact
(def tx-data (mapcat datomic-gen/schema->transaction datomic-schemas))
@(d/transact conn tx-data)
```

### 4. Set Up GraphQL with Lacinia

```clojure
(require '[com.walmartlabs.lacinia :as lacinia]
         '[com.walmartlabs.lacinia.schema :as schema])

;; Create resolvers for each entity
(def user-resolver 
  (core/create-resolver :User conn (first parsed-schemas)))

(def post-resolver 
  (core/create-resolver :Post conn (second parsed-schemas)))

;; Attach resolvers to schema
(def executable-schema
  (-> lacinia-schema
      (assoc-in [:queries :User :resolve] user-resolver)
      (assoc-in [:queries :Post :resolve] post-resolver)
      schema/compile))

;; Execute GraphQL queries
(lacinia/execute executable-schema 
                 "{ User(email: \"user@example.com\") { id username email } }" 
                 nil nil)
```

## Custom Malli Properties

Ringline uses custom Malli properties to control schema generation:

- **`:ringline/datomic-ns`** - Namespace for Datomic attributes (e.g., `:user` → `:user/id`)
- **`:ringline/query-root`** - Mark entity as GraphQL query root (`true`/`false`)
- **`:ringline/searchable`** - Vector of field names to include as GraphQL query arguments
- **`:ringline/ref-to`** - Mark field as reference to another entity (e.g., `{:ringline/ref-to :user}`)

## Type Mappings

### Malli → Datomic
- `:string` → `:db.type/string`
- `:int` → `:db.type/long`
- `:uuid` → `:db.type/uuid`
- `:boolean` → `:db.type/boolean`
- `:double` → `:db.type/double`
- `:keyword` → `:db.type/keyword`
- Fields with `:ringline/ref-to` → `:db.type/ref`

### Malli → GraphQL
- `:string` → `String`
- `:int` → `Int`
- `:uuid` → `ID`
- `:boolean` → `Boolean`
- `:double` → `Float`
- `:keyword` → `String`
- `:enum` → `String`
- `[:vector T]` → `(list T)`

## Development

### Running Tests

```bash
clojure -M:test
```

### REPL Development

```bash
clojure -M:dev
```

## License

Copyright © 2026

## Contributing

This is an experimental framework. Contributions welcome!

