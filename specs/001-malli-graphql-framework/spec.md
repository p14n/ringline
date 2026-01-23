# Feature Specification: Malli-GraphQL Framework

**Feature Branch**: `001-malli-graphql-framework`
**Created**: 2026-01-23
**Status**: Draft
**Input**: User description: "Model entities and relationships in malli, convert to lacinia graphql schema and datomic schema, convert graphql queries to datomic pull queries"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define Data Model with Malli (Priority: P1)

A developer defines their application's data model using Malli schemas with custom properties. The framework reads these schemas and understands entity relationships, field types, and metadata like Datomic namespaces and query roots.

**Why this priority**: This is the foundation of the entire framework. Without the ability to define and parse Malli schemas, no other functionality can work. This delivers immediate value by providing a single source of truth for the data model.

**Independent Test**: Can be fully tested by defining a Malli schema with entities and relationships, then verifying the framework correctly parses the schema structure, extracts metadata properties, and identifies entity relationships.

**Acceptance Scenarios**:

1. **Given** a Malli schema defining a User entity with fields, **When** the developer provides the schema to the framework, **Then** the framework extracts all field definitions and their types
2. **Given** a Malli schema with custom properties for Datomic namespace, **When** the schema is processed, **Then** the framework correctly identifies and stores the namespace metadata
3. **Given** two Malli schemas with a relationship between them, **When** both schemas are processed, **Then** the framework identifies the relationship type and cardinality
4. **Given** a Malli schema with query root properties, **When** the schema is processed, **Then** the framework marks the entity as a valid GraphQL query entry point

---

### User Story 2 - Generate Datomic Schema (Priority: P2)

A developer uses the framework to automatically generate a Datomic schema from their Malli data model. The generated schema includes proper attribute definitions, cardinality, types, and namespaces based on Malli properties.

**Why this priority**: This enables database persistence without manual schema writing. It's the second priority because it builds on the data model parsing and provides immediate practical value for data storage.

**Independent Test**: Can be fully tested by providing a complete Malli data model and verifying the generated Datomic schema contains correct attribute definitions, proper namespacing, correct cardinality settings, and valid Datomic types.

**Acceptance Scenarios**:

1. **Given** a Malli schema with string and number fields, **When** Datomic schema is generated, **Then** fields are converted to appropriate Datomic types (db.type/string, db.type/long, etc.)
2. **Given** a Malli schema with a custom Datomic namespace property, **When** Datomic schema is generated, **Then** all attributes use the specified namespace
3. **Given** a Malli schema with a one-to-many relationship, **When** Datomic schema is generated, **Then** the relationship attribute has cardinality db.cardinality/many
4. **Given** a Malli schema with required and optional fields, **When** Datomic schema is generated, **Then** the schema correctly reflects field optionality

---

### User Story 3 - Generate Lacinia GraphQL Schema (Priority: P3)

A developer uses the framework to automatically generate a Lacinia GraphQL schema from their Malli data model. The generated schema includes object types, fields, relationships, and query roots based on Malli properties.

**Why this priority**: This completes the schema generation trio, enabling API exposure. It's third priority because it depends on the data model parsing but can be developed independently of Datomic schema generation.

**Independent Test**: Can be fully tested by providing a Malli data model and verifying the generated Lacinia schema contains correct GraphQL object types, proper field types, correct relationship definitions, and valid query root definitions.

**Acceptance Scenarios**:

1. **Given** a Malli schema with basic fields, **When** Lacinia schema is generated, **Then** fields are converted to appropriate GraphQL types (String, Int, Float, Boolean, ID)
2. **Given** a Malli schema marked as a query root, **When** Lacinia schema is generated, **Then** the entity appears in the GraphQL Query type
3. **Given** a Malli schema with relationships to other entities, **When** Lacinia schema is generated, **Then** relationships are represented as GraphQL object references
4. **Given** a Malli schema with searchable parameter properties, **When** Lacinia schema is generated, **Then** query fields include appropriate search arguments

---

### User Story 4 - Convert GraphQL Queries to Datomic Pull (Priority: P4)

A developer receives a GraphQL query and the framework automatically converts it to an equivalent Datomic pull query. The conversion respects the requested fields, follows relationships, and handles nested queries.

**Why this priority**: This enables the actual query execution flow. It's fourth priority because it requires both schema generation features to be complete and represents the runtime query processing.

**Independent Test**: Can be fully tested by providing a GraphQL query AST and verifying the generated Datomic pull pattern correctly specifies requested fields, includes nested relationship pulls, and handles query arguments for filtering.

**Acceptance Scenarios**:

1. **Given** a GraphQL query requesting specific fields, **When** converted to Datomic pull, **Then** the pull pattern includes only the requested attributes
2. **Given** a GraphQL query with nested relationships, **When** converted to Datomic pull, **Then** the pull pattern includes nested pull expressions for relationships
3. **Given** a GraphQL query with search parameters, **When** converted to Datomic pull, **Then** the framework generates appropriate Datomic query clauses for filtering
4. **Given** a GraphQL query requesting multiple entities, **When** converted to Datomic pull, **Then** the framework generates separate pull patterns for each entity type

---

### User Story 5 - Convert Datomic Responses to Lacinia Format (Priority: P5)

A developer receives Datomic query results and the framework automatically converts them to the format expected by Lacinia for GraphQL responses. The conversion handles entity maps, relationships, and type coercion.

**Why this priority**: This completes the query execution flow. It's the final priority because it depends on all previous features and represents the last step in the request-response cycle.

**Independent Test**: Can be fully tested by providing Datomic entity results and verifying the converted output matches Lacinia's expected format, correctly resolves relationships, and properly coerces types.

**Acceptance Scenarios**:

1. **Given** a Datomic entity result, **When** converted to Lacinia format, **Then** attribute names are transformed to match GraphQL field names
2. **Given** a Datomic result with relationship references, **When** converted to Lacinia format, **Then** relationships are resolved to nested entity maps
3. **Given** a Datomic result with multiple entities, **When** converted to Lacinia format, **Then** the output is a properly formatted list of GraphQL objects
4. **Given** a Datomic result with null or missing values, **When** converted to Lacinia format, **Then** the output correctly represents nullability according to the GraphQL schema

---

### Edge Cases

- What happens when a Malli schema contains unsupported types for GraphQL or Datomic?
- How does the system handle circular relationships between entities?
- What happens when a GraphQL query requests fields that don't exist in the Datomic schema?
- How does the framework handle Malli schemas that change after Datomic schema has been transacted?
- What happens when Datomic returns entities that are missing required GraphQL fields?
- How does the system handle very deep nested queries (potential performance issues)?
- What happens when Malli property metadata is missing or invalid?

## Requirements *(mandatory)*

### Functional Requirements

#### Malli Schema Processing

- **FR-001**: Framework MUST accept Malli schemas as input for data model definition
- **FR-002**: Framework MUST extract field names, types, and validation rules from Malli schemas
- **FR-003**: Framework MUST support custom Malli properties for framework-specific metadata
- **FR-004**: Framework MUST identify relationships between entities defined in separate Malli schemas
- **FR-005**: Framework MUST determine relationship cardinality (one-to-one, one-to-many, many-to-many) from Malli schema structure

#### Datomic Schema Generation

- **FR-006**: Framework MUST generate valid Datomic schema transactions from Malli schemas
- **FR-007**: Framework MUST map Malli types to appropriate Datomic types (string, long, double, boolean, instant, uuid, ref)
- **FR-008**: Framework MUST use Malli property metadata to set Datomic attribute namespaces
- **FR-009**: Framework MUST set correct cardinality (one or many) for Datomic attributes based on Malli schema
- **FR-010**: Framework MUST handle optional vs required fields in Datomic schema generation
- **FR-011**: Framework MUST generate db.type/ref attributes for entity relationships

#### Lacinia GraphQL Schema Generation

- **FR-012**: Framework MUST generate valid Lacinia schema definitions from Malli schemas
- **FR-013**: Framework MUST map Malli types to appropriate GraphQL types (String, Int, Float, Boolean, ID)
- **FR-014**: Framework MUST create GraphQL object types for each entity in the Malli schema
- **FR-015**: Framework MUST use Malli property metadata to identify query root entities
- **FR-016**: Framework MUST generate GraphQL Query type with fields for each query root entity
- **FR-017**: Framework MUST use Malli property metadata to identify searchable parameters
- **FR-018**: Framework MUST generate query arguments for searchable fields
- **FR-019**: Framework MUST represent entity relationships as GraphQL object type references

#### GraphQL to Datomic Query Conversion

- **FR-020**: Framework MUST convert GraphQL query selections to Datomic pull patterns
- **FR-021**: Framework MUST include only requested fields in the Datomic pull pattern
- **FR-022**: Framework MUST handle nested GraphQL selections by generating nested pull patterns
- **FR-023**: Framework MUST convert GraphQL query arguments to Datomic query where clauses
- **FR-024**: Framework MUST support filtering by searchable parameters defined in Malli properties
- **FR-025**: Framework MUST handle multiple entity queries in a single GraphQL request

#### Datomic to Lacinia Response Conversion

- **FR-026**: Framework MUST convert Datomic entity maps to Lacinia-compatible response format
- **FR-027**: Framework MUST transform Datomic attribute names to GraphQL field names
- **FR-028**: Framework MUST resolve Datomic entity references to nested GraphQL objects
- **FR-029**: Framework MUST handle null and missing values according to GraphQL schema nullability
- **FR-030**: Framework MUST convert Datomic types to GraphQL-compatible types in responses
- **FR-031**: Framework MUST support returning lists of entities for collection queries

#### Integration Requirements

- **FR-032**: Framework MUST integrate with Ring middleware for HTTP request handling
- **FR-033**: Framework MUST provide functions that can be used as Lacinia resolvers
- **FR-034**: Framework MUST accept a Datomic database connection for query execution

### Key Entities

- **Malli Schema**: Represents the data model definition with fields, types, validation rules, and custom properties for framework metadata
- **Datomic Schema**: Generated database schema with attribute definitions, types, cardinality, and namespaces
- **Lacinia Schema**: Generated GraphQL schema with object types, fields, query roots, and arguments
- **GraphQL Query**: Incoming API request specifying which fields and entities to retrieve
- **Datomic Pull Pattern**: Database query pattern derived from GraphQL query selections
- **Entity Map**: Data structure representing a single entity with its attributes and relationships

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can define a complete data model using only Malli schemas without writing separate Datomic or GraphQL schema definitions
- **SC-002**: Schema generation completes in under 1 second for data models with up to 50 entities
- **SC-003**: Generated Datomic schemas are valid and can be transacted without errors
- **SC-004**: Generated Lacinia schemas are valid and can execute GraphQL queries without schema errors
- **SC-005**: GraphQL queries are correctly converted to Datomic pull patterns that retrieve exactly the requested data
- **SC-006**: Query responses match the structure defined in the GraphQL schema with correct types and nesting
- **SC-007**: Framework handles queries with up to 5 levels of nested relationships without errors
- **SC-008**: Developers can add new entities to the data model and regenerate schemas without breaking existing functionality
- **SC-009**: Framework correctly handles at least 10 different Malli field types (string, int, double, boolean, uuid, instant, keyword, enum, vector, map)
- **SC-010**: 100% of searchable parameters defined in Malli properties are available as GraphQL query arguments

## Assumptions

- Developers using this framework have basic knowledge of Malli schema syntax
- The Datomic database is already set up and accessible via connection
- Ring middleware is configured to route GraphQL requests to the framework
- Malli schemas follow a consistent structure with proper type definitions
- Entity relationships are explicitly defined using Malli reference types or custom properties
- The framework will be used in a single-database context (not multi-tenant with separate databases)
- GraphQL queries will be validated by Lacinia before reaching the framework's conversion logic
- Datomic attribute namespaces will be unique across the application to avoid conflicts
- The framework will operate in a synchronous request-response model (no subscriptions or streaming)
- Performance optimization for very large result sets will be handled by application-level pagination
