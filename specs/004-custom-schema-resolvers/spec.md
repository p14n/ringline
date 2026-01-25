# Feature Specification: Custom Query and Mutation Schema Support

**Feature Branch**: `004-custom-schema-resolvers`
**Created**: 2026-01-25
**Status**: Draft
**Input**: User description: "Provide the ability to specify customer query and mutation schema parts in malli, to support the use of manually-created resolvers."

## Clarifications

### Session 2026-01-25

- Q: When a custom query/mutation name conflicts with an auto-generated one, how should the framework handle it? → A: Custom operation takes precedence (silently overrides auto-generated)
- Q: How should developers specify custom query/mutation metadata in Malli schemas? → A: Use Malli schema properties (e.g., `:ringline/custom-query` with name, args, return-type)
- Q: When a custom query/mutation is defined but no resolver is attached, what should happen? → A: Validation error during framework initialization (fail fast)
- Q: How should custom schemas reference entity types defined in entity schemas? → A: Use entity name as keyword (e.g., `:User` references User entity schema)
- Q: What does "seamless integration" mean for custom and auto-generated operations? → A: Zero breaking changes to existing code

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define Custom Query with Manual Resolver (Priority: P1)

A developer needs to create a custom GraphQL query that cannot be automatically generated from entity schemas (e.g., complex aggregations, multi-entity searches, or business logic queries). They want to define the query's input arguments and return type in Malli, then provide their own resolver function.

**Why this priority**: This is the core capability that enables developers to extend beyond auto-generated CRUD operations. Without this, the framework is limited to simple entity queries.

**Independent Test**: Can be fully tested by defining a custom query schema in Malli (e.g., "searchUsers" with filters), attaching a manual resolver function, and verifying the query appears in the generated GraphQL schema with correct types.

**Acceptance Scenarios**:

1. **Given** a Malli schema defining a custom query with arguments and return type, **When** the framework processes the schema, **Then** the query appears in the GraphQL schema with correct argument types and return type
2. **Given** a custom query schema and a manually-written resolver function, **When** the developer attaches the resolver to the schema, **Then** GraphQL queries execute using the manual resolver
3. **Given** a custom query with complex argument types (nested maps, enums, optional fields), **When** the schema is generated, **Then** all argument types are correctly represented in GraphQL

---

### User Story 2 - Define Custom Mutation with Manual Resolver (Priority: P1)

A developer needs to create a custom GraphQL mutation that involves complex business logic beyond simple create/update/delete operations (e.g., "approveOrder", "transferOwnership", "bulkImport"). They want to define the mutation's input schema and return type in Malli, then provide their own resolver function.

**Why this priority**: Custom mutations are equally critical as custom queries for real-world applications. Many business operations don't map to simple CRUD patterns.

**Independent Test**: Can be fully tested by defining a custom mutation schema in Malli (e.g., "approveOrder" with orderId and approverNotes), attaching a manual resolver, and verifying the mutation executes correctly.

**Acceptance Scenarios**:

1. **Given** a Malli schema defining a custom mutation with input type and return type, **When** the framework processes the schema, **Then** the mutation appears in the GraphQL schema with correct input object type
2. **Given** a custom mutation schema and a manually-written resolver function, **When** the developer attaches the resolver, **Then** GraphQL mutations execute using the manual resolver
3. **Given** a custom mutation that returns a custom result type (not an entity), **When** the mutation executes, **Then** the result is correctly transformed to GraphQL format

---

### User Story 3 - Mix Auto-Generated and Custom Schemas (Priority: P2)

A developer has entity schemas with auto-generated queries and mutations, but also needs custom operations. They want to define both in the same schema map and have the framework merge them into a single GraphQL schema.

**Why this priority**: Real applications need both auto-generated CRUD and custom operations. This ensures the framework supports realistic use cases.

**Independent Test**: Can be fully tested by initializing the framework with both entity schemas (with :ringline/query-root) and custom query/mutation schemas, then verifying the resulting GraphQL schema contains both types of operations.

**Acceptance Scenarios**:

1. **Given** a schema map containing both entity schemas and custom query schemas, **When** init-framework is called, **Then** the resulting GraphQL schema contains both auto-generated and custom queries
2. **Given** entity schemas with :ringline/mutations and custom mutation schemas, **When** the framework merges them, **Then** all mutations appear in the GraphQL schema without conflicts
3. **Given** custom and auto-generated operations with different naming patterns, **When** resolvers are attached, **Then** each operation uses its correct resolver (auto or manual)

---

### Edge Cases

- When a custom query/mutation name conflicts with an auto-generated one, the custom operation takes precedence and silently overrides the auto-generated operation
- Custom queries/mutations reference entity types using entity name as keyword (e.g., `:User` references User entity schema)
- Custom mutations can reference enums and custom scalars using the same keyword-based approach as entity types
- Validation errors in custom query/mutation schemas are reported during framework initialization with clear error messages
- When a custom query/mutation is defined but no resolver is attached, the framework throws a validation error during initialization (fail fast)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow developers to define custom GraphQL queries using Malli schema properties (e.g., `:ringline/custom-query`) that specify query name, arguments, and return type
- **FR-002**: System MUST allow developers to define custom GraphQL mutations using Malli schema properties (e.g., `:ringline/custom-mutation`) that specify mutation name, input type, and return type
- **FR-003**: System MUST generate GraphQL schema definitions (queries, mutations, input objects) from custom Malli schemas
- **FR-004**: System MUST provide a mechanism to attach manually-written resolver functions to custom queries and mutations
- **FR-005**: System MUST merge custom queries/mutations with auto-generated ones from entity schemas into a single GraphQL schema
- **FR-006**: System MUST validate custom schemas during framework initialization and report errors if required fields (name, args, return type) are missing or if a resolver is not attached
- **FR-007**: System MUST support all Malli types in custom query arguments (primitives, enums, maps, vectors, optional fields)
- **FR-008**: System MUST support custom return types that reference entity types, enums, and custom scalars using entity name as keyword (e.g., `:User`, `:Episode`)
- **FR-009**: When a custom operation name conflicts with an auto-generated operation, the custom operation MUST take precedence and override the auto-generated one
- **FR-010**: System MUST preserve existing auto-generated query and mutation functionality when custom schemas are added, ensuring zero breaking changes to existing code

### Key Entities *(include if feature involves data)*

- **Custom Query Schema**: Malli schema with `:ringline/custom-query` property containing query name, arguments map (Malli schema), and return type (keyword or Malli schema)
- **Custom Mutation Schema**: Malli schema with `:ringline/custom-mutation` property containing mutation name, input schema (Malli schema), and return type (keyword or Malli schema)
- **Resolver Attachment**: Mapping between custom operation names and manually-written resolver functions

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can define a custom query in under 10 lines of Malli schema code
- **SC-002**: Custom queries and mutations integrate with zero breaking changes to existing code using auto-generated operations
- **SC-003**: Framework initialization time increases by less than 10% when adding custom schemas
- **SC-004**: 100% of Malli types supported in entity schemas are also supported in custom query/mutation schemas
