# Feature Specification: GraphQL Mutations

**Feature Branch**: `002-graphql-mutations`
**Created**: 2026-01-24
**Status**: Draft
**Input**: User description: "Add the ability to define mutations"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define Mutation Operations in Malli (Priority: P1)

A developer defines mutation operations (create, update, delete) for their entities using Malli schemas with custom properties. The framework reads these mutation definitions and understands which operations are allowed, what input data is required, and what validation rules apply.

**Why this priority**: This is the foundation for mutation support. Without the ability to define and parse mutation schemas, no mutation functionality can work. This delivers immediate value by providing a declarative way to specify data modification operations.

**Independent Test**: Can be fully tested by defining a Malli schema with mutation properties, then verifying the framework correctly parses the mutation definitions, extracts input requirements, and identifies allowed operations.

**Acceptance Scenarios**:

1. **Given** a Malli schema with mutation properties defining create operation, **When** the developer provides the schema to the framework, **Then** the framework extracts the create mutation definition with required input fields
2. **Given** a Malli schema with mutation properties defining update operation, **When** the schema is processed, **Then** the framework identifies which fields can be updated and their validation rules
3. **Given** a Malli schema with mutation properties defining delete operation, **When** the schema is processed, **Then** the framework recognizes the delete mutation and its identifier requirements
4. **Given** a Malli schema without mutation properties, **When** the schema is processed, **Then** the framework generates no mutation definitions for that entity

---

### User Story 2 - Generate Lacinia Mutation Schema (Priority: P2)

A developer uses the framework to automatically generate Lacinia GraphQL mutation definitions from their Malli mutation schemas. The generated mutations include proper input types, return types, and argument definitions.

**Why this priority**: This enables the GraphQL API to expose mutation operations. It's second priority because it builds on the mutation definition parsing and provides the API surface for data modifications.

**Independent Test**: Can be fully tested by providing a Malli schema with mutation definitions and verifying the generated Lacinia schema contains correct mutation fields, input object types, and return types.

**Acceptance Scenarios**:

1. **Given** a Malli schema with create mutation definition, **When** Lacinia mutation schema is generated, **Then** a create mutation field appears in the GraphQL Mutation type with appropriate input arguments
2. **Given** a Malli schema with update mutation definition, **When** Lacinia mutation schema is generated, **Then** an update mutation field is created with ID argument and update input type
3. **Given** a Malli schema with delete mutation definition, **When** Lacinia mutation schema is generated, **Then** a delete mutation field is created that accepts an ID and returns success status
4. **Given** a Malli schema with validation rules on mutation inputs, **When** Lacinia mutation schema is generated, **Then** the input types reflect the validation constraints

---

### User Story 3 - Convert Mutation Inputs to Datomic Transactions (Priority: P3)

A developer receives a GraphQL mutation request and the framework automatically converts it to Datomic transaction data. The conversion handles entity creation, updates, and deletions with proper attribute mapping and validation.

**Why this priority**: This enables the actual data modification flow. It's third priority because it requires both mutation schema generation and depends on understanding the data model.

**Independent Test**: Can be fully tested by providing a GraphQL mutation input and verifying the generated Datomic transaction data correctly represents the create/update/delete operation with proper attribute names and values.

**Acceptance Scenarios**:

1. **Given** a GraphQL create mutation with input data, **When** converted to Datomic transaction, **Then** the transaction contains proper :db/add operations with namespaced attributes
2. **Given** a GraphQL update mutation with partial data, **When** converted to Datomic transaction, **Then** the transaction updates only the specified fields for the identified entity
3. **Given** a GraphQL delete mutation with entity ID, **When** converted to Datomic transaction, **Then** the transaction contains :db/retractEntity operation for the specified entity
4. **Given** a GraphQL mutation with nested relationship data, **When** converted to Datomic transaction, **Then** the transaction properly handles relationship references

---

### User Story 4 - Execute Mutations and Return Results (Priority: P4)

A developer executes a mutation through the GraphQL API and receives the result in the expected format. The framework handles transaction execution, error handling, and response formatting.

**Why this priority**: This completes the mutation execution flow. It's fourth priority because it integrates all previous mutation features and provides the end-to-end functionality.

**Independent Test**: Can be fully tested by executing a mutation operation and verifying the response contains the mutated entity data, proper error messages for validation failures, and correct status indicators.

**Acceptance Scenarios**:

1. **Given** a valid create mutation request, **When** executed, **Then** the response contains the newly created entity with all fields populated
2. **Given** a valid update mutation request, **When** executed, **Then** the response contains the updated entity reflecting the changes
3. **Given** a valid delete mutation request, **When** executed, **Then** the response indicates successful deletion
4. **Given** an invalid mutation request with validation errors, **When** executed, **Then** the response contains clear error messages describing the validation failures

---

### Edge Cases

- What happens when a mutation attempts to create an entity with a duplicate unique identifier?
- How does the system handle mutations that violate referential integrity (e.g., referencing non-existent entities)?
- What happens when a mutation input contains fields that don't exist in the schema?
- How does the framework handle concurrent mutations on the same entity?
- What happens when a mutation transaction fails partway through (e.g., database connection lost)?
- How does the system handle mutations on entities with required fields that aren't provided?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow developers to define create, update, and delete mutation operations for entities using Malli schema properties
- **FR-002**: System MUST generate Lacinia GraphQL mutation definitions from Malli mutation schemas
- **FR-003**: System MUST generate appropriate GraphQL input types for mutation arguments based on entity field definitions
- **FR-004**: System MUST convert GraphQL mutation inputs to Datomic transaction data with proper attribute namespacing
- **FR-005**: System MUST validate mutation inputs against Malli schemas before executing transactions
- **FR-006**: System MUST execute Datomic transactions for create, update, and delete operations
- **FR-007**: System MUST return the mutated entity data in GraphQL format after successful mutations
- **FR-008**: System MUST return clear error messages when mutations fail validation or execution
- **FR-009**: System MUST handle partial updates where only specified fields are modified
- **FR-010**: System MUST support mutations on entities with relationships to other entities
- **FR-011**: System MUST preserve existing query functionality while adding mutation support
- **FR-012**: System MUST allow developers to specify which mutation operations are allowed per entity (create, update, delete can be independently enabled/disabled)

### Key Entities

- **Mutation Definition**: Represents a mutation operation (create/update/delete) for an entity, including allowed operations, input requirements, and validation rules
- **Mutation Input**: The data provided by the client for a mutation operation, validated against the entity's Malli schema
- **Mutation Result**: The outcome of a mutation operation, including the mutated entity data or error information

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can define mutation operations for entities in under 5 minutes using Malli schema properties
- **SC-002**: Generated mutation schemas correctly represent all defined operations with proper input and return types
- **SC-003**: Mutation execution completes in under 500 milliseconds for simple create/update/delete operations
- **SC-004**: 100% of mutation validation errors provide clear, actionable error messages to clients
- **SC-005**: Mutation operations maintain data integrity with zero instances of orphaned references or constraint violations
- **SC-006**: Developers can execute end-to-end mutation workflows (define, generate, execute) without writing custom transaction code
