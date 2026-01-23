# Data Model: Malli-GraphQL Framework

**Date**: 2026-01-23  
**Feature**: 001-malli-graphql-framework

## Overview

This framework operates on data models defined as Malli schemas. The framework itself doesn't define business entities but rather processes entity definitions provided by users. This document describes the internal data structures used by the framework.

## Framework Internal Entities

### ParsedSchema

Represents a Malli schema after parsing and metadata extraction.

**Fields**:
- `schema-name` (keyword) - The entity name (e.g., :User, :Post)
- `fields` (vector of FieldDefinition) - All fields in the entity
- `properties` (map) - Custom Malli properties attached to the schema
- `relationships` (vector of Relationship) - References to other entities

**Validation Rules**:
- schema-name must be a namespaced or simple keyword
- fields must be a non-empty vector
- properties may be empty but must be a map

**Example**:
```clojure
{:schema-name :User
 :fields [{:name :user/id :type :uuid :required true}
          {:name :user/email :type :string :required true}
          {:name :user/posts :type :ref :cardinality :many}]
 :properties {:ringline/datomic-ns "user"
              :ringline/query-root true}
 :relationships [{:field :user/posts :target :Post :cardinality :many}]}
```

---

### FieldDefinition

Represents a single field within an entity schema.

**Fields**:
- `name` (keyword) - Field name (may be namespaced)
- `type` (keyword) - Malli type (:string, :int, :double, :boolean, :uuid, :inst, :keyword, :ref, :enum, :vector, :map)
- `required` (boolean) - Whether field is required
- `cardinality` (keyword) - :one or :many (for collections)
- `enum-values` (vector, optional) - For enum types
- `properties` (map, optional) - Field-level custom properties

**Validation Rules**:
- name must be a keyword
- type must be one of the supported types
- cardinality defaults to :one
- enum-values required if type is :enum

---

### Relationship

Represents a relationship between two entities.

**Fields**:
- `field` (keyword) - The field name that holds the reference
- `source` (keyword) - Source entity name
- `target` (keyword) - Target entity name
- `cardinality` (keyword) - :one or :many
- `bidirectional` (boolean) - Whether relationship has reverse reference

**Validation Rules**:
- source and target must be valid entity names
- cardinality must be :one or :many
- bidirectional defaults to false

---

### DatomicSchema

Represents generated Datomic schema ready for transaction.

**Fields**:
- `attributes` (vector of maps) - Datomic attribute definitions
- `source-entity` (keyword) - Which entity this schema was generated from

**Structure**:
Each attribute map contains:
- `:db/ident` - Attribute identifier (keyword)
- `:db/valueType` - Datomic type keyword
- `:db/cardinality` - :db.cardinality/one or :db.cardinality/many
- `:db/doc` (optional) - Documentation string

**Example**:
```clojure
{:source-entity :User
 :attributes [{:db/ident :user/id
               :db/valueType :db.type/uuid
               :db/cardinality :db.cardinality/one
               :db/unique :db.unique/identity}
              {:db/ident :user/email
               :db/valueType :db.type/string
               :db/cardinality :db.cardinality/one}
              {:db/ident :user/posts
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/many}]}
```

---

### LaciniaSchema

Represents generated Lacinia GraphQL schema.

**Fields**:
- `objects` (map) - GraphQL object type definitions
- `queries` (map) - Top-level query definitions
- `enums` (map, optional) - Enum type definitions

**Structure**:
```clojure
{:objects {:User {:fields {:id {:type '(non-null ID)}
                           :email {:type '(non-null String)}
                           :posts {:type '(list :Post)}}}}
 :queries {:user {:type :User
                  :args {:id {:type '(non-null ID)}}
                  :resolve :resolve-user}
           :users {:type '(list :User)
                   :args {:email {:type 'String}}
                   :resolve :resolve-users}}}
```

---

### QueryContext

Represents the context for converting a GraphQL query to Datomic.

**Fields**:
- `entity-type` (keyword) - The root entity being queried
- `selections` (vector) - GraphQL field selections
- `arguments` (map) - Query arguments for filtering
- `nested-queries` (map) - Nested selections for relationships

**Example**:
```clojure
{:entity-type :User
 :selections [:id :email :posts]
 :arguments {:email "user@example.com"}
 :nested-queries {:posts {:selections [:id :title :created-at]}}}
```

---

### PullPattern

Represents a Datomic pull pattern generated from GraphQL query.

**Fields**:
- `pattern` (vector) - Datomic pull syntax
- `entity-id` (any, optional) - Specific entity ID to pull
- `where-clauses` (vector, optional) - Datalog where clauses for filtering

**Example**:
```clojure
{:pattern [:user/id :user/email {:user/posts [:post/id :post/title :post/created-at]}]
 :where-clauses [['?e :user/email "user@example.com"]]}
```

---

## State Transitions

### Schema Processing Flow

1. **Input**: Raw Malli schema (map/vector)
2. **Parse**: Extract fields, properties, relationships → ParsedSchema
3. **Generate Datomic**: ParsedSchema → DatomicSchema
4. **Generate Lacinia**: ParsedSchema → LaciniaSchema

### Query Execution Flow

1. **Input**: GraphQL query (from Lacinia)
2. **Parse**: Extract selections and arguments → QueryContext
3. **Convert**: QueryContext → PullPattern
4. **Execute**: PullPattern + Datomic DB → Entity results
5. **Transform**: Entity results → GraphQL response format

---

## Relationships Between Entities

- ParsedSchema contains FieldDefinitions and Relationships
- DatomicSchema is generated from ParsedSchema
- LaciniaSchema is generated from ParsedSchema
- QueryContext references entity types from ParsedSchema
- PullPattern is generated from QueryContext

All entities are immutable data structures (Clojure maps and vectors).

