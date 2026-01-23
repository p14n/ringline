# Research: Malli-GraphQL Framework

**Date**: 2026-01-23  
**Feature**: 001-malli-graphql-framework

## Research Areas

### 1. Malli Schema Introspection and Properties

**Decision**: Use Malli's `m/properties` and `m/children` functions for schema introspection

**Rationale**: 
- Malli provides built-in functions for walking schema trees and extracting metadata
- `m/properties` retrieves custom properties attached to schemas
- `m/children` allows recursive traversal of nested schemas
- `m/type` identifies the schema type (e.g., :map, :vector, :string)
- This approach is idiomatic and leverages Malli's existing API

**Alternatives Considered**:
- Manual schema parsing: Rejected because it would duplicate Malli's internal logic and be fragile to schema format changes
- Schema compilation: Rejected because we need the raw schema structure, not compiled validators

**Implementation Notes**:
- Custom properties will use namespaced keywords (e.g., `:ringline/datomic-ns`, `:ringline/query-root`, `:ringline/searchable`)
- Schema walking will be recursive to handle nested entity definitions
- Type mapping will use a lookup table from Malli types to Datomic/GraphQL types

---

### 2. Datomic Schema Generation Patterns

**Decision**: Generate Datomic schema as transaction data (vector of attribute maps)

**Rationale**:
- Datomic schemas are defined as data (maps with :db/ident, :db/valueType, :db/cardinality)
- This aligns with Clojure's data-driven approach
- Generated schemas can be validated before transacting
- Easy to test by comparing generated data structures

**Alternatives Considered**:
- String-based schema generation: Rejected because Datomic uses data, not strings
- Direct database transacting: Rejected because framework should return data, not perform side effects

**Type Mapping**:
```clojure
{:string    :db.type/string
 :int       :db.type/long
 :double    :db.type/double
 :boolean   :db.type/boolean
 :uuid      :db.type/uuid
 :inst      :db.type/instant
 :keyword   :db.type/keyword
 :ref       :db.type/ref}
```

**Cardinality Rules**:
- Vector/sequential schemas → :db.cardinality/many
- Single value schemas → :db.cardinality/one
- Ref types always get :db.type/ref

---

### 3. Lacinia Schema Generation Patterns

**Decision**: Generate Lacinia schema as EDN data structure following Lacinia's schema format

**Rationale**:
- Lacinia schemas are defined as Clojure data (nested maps)
- Schema structure: {:objects {...}, :queries {...}, :mutations {...}}
- Supports compile-time validation via `lacinia.schema/compile`
- Resolver functions can be attached after schema generation

**Alternatives Considered**:
- GraphQL SDL strings: Rejected because Lacinia uses EDN, not SDL
- Schema builders: Rejected because direct data generation is simpler

**Type Mapping**:
```clojure
{:string    'String
 :int       'Int
 :double    'Float
 :boolean   'Boolean
 :uuid      'ID
 :inst      'String  ; ISO-8601 formatted
 :keyword   'String
 :enum      (list 'non-null (list 'enum ...))}
```

**Query Root Generation**:
- Entities marked with `:ringline/query-root true` become top-level queries
- Searchable parameters become query arguments
- Relationships become nested object fields

---

### 4. GraphQL Query to Datomic Pull Conversion

**Decision**: Parse Lacinia's field selection and convert to Datomic pull syntax

**Rationale**:
- Lacinia provides field selection in resolver context
- Datomic pull uses vector/map syntax: `[:field1 :field2 {:relationship [:nested-field]}]`
- Direct mapping from GraphQL selections to pull patterns
- Supports nested queries naturally

**Alternatives Considered**:
- Datalog queries: Rejected because pull is more efficient for entity retrieval
- Entity API: Rejected because pull is more declarative and performant

**Conversion Rules**:
- GraphQL field → Datomic attribute keyword
- Nested selection → Nested pull map
- Arguments → Separate datalog query for filtering
- Multiple entities → Multiple pull operations

---

### 5. Datomic Response to GraphQL Transformation

**Decision**: Transform Datomic entity maps to match GraphQL schema structure

**Rationale**:
- Datomic returns entity maps with keyword keys
- GraphQL expects camelCase field names (configurable)
- Relationships need to be resolved recursively
- Null handling must respect GraphQL schema nullability

**Alternatives Considered**:
- Lazy resolution: Rejected because it complicates error handling
- Direct passthrough: Rejected because field name conventions differ

**Transformation Rules**:
- Keyword keys → String/camelCase keys (configurable)
- :db/id → Omit or map to GraphQL ID type
- Ref values → Recursively transform nested entities
- Missing values → nil (respecting schema nullability)
- Collections → Vectors/lists

---

### 6. Ring Integration Pattern

**Decision**: Provide middleware and helper functions, not a complete Ring handler

**Rationale**:
- Framework should be composable, not prescriptive
- Developers may have existing Ring middleware stacks
- Lacinia already provides Ring integration
- Framework focuses on schema generation and query conversion

**Alternatives Considered**:
- Complete Ring handler: Rejected because it reduces flexibility
- Standalone server: Rejected because this is a library, not an application

**Integration Points**:
- Schema generation functions (called at startup)
- Resolver functions (used in Lacinia schema)
- Query conversion utilities (used in custom resolvers)

---

## Summary

All research areas resolved. No NEEDS CLARIFICATION items remain. The framework will:

1. Use Malli's introspection API for schema parsing
2. Generate Datomic schemas as transaction data
3. Generate Lacinia schemas as EDN data structures
4. Convert GraphQL selections to Datomic pull patterns
5. Transform Datomic entities to GraphQL-compatible maps
6. Integrate with Ring via composable functions, not monolithic handlers

Ready to proceed to Phase 1: Design & Contracts.

