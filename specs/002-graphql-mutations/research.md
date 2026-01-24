# Research: GraphQL Mutations

**Feature**: 002-graphql-mutations  
**Date**: 2026-01-24  
**Status**: Complete

## Overview

This document consolidates research findings for implementing mutation support in the Ringline Malli-GraphQL Framework. All technical context was already well-defined from the existing framework implementation (feature 001), so this research focuses on mutation-specific patterns and best practices.

## Research Questions & Findings

### 1. Malli Custom Properties for Mutations

**Question**: How should we extend Malli schemas to define mutation operations?

**Decision**: Use `:ringline/mutations` property on entity schemas

**Rationale**: 
- Consistent with existing `:ringline/query-root`, `:ringline/searchable`, `:ringline/ref-to` pattern
- Allows declarative specification of allowed operations per entity
- Enables fine-grained control (e.g., allow create/update but not delete)
- Schema example:
  ```clojure
  [:map
   {:ringline/datomic-ns :user
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}}  ; NEW
   [:id :uuid]
   [:username :string]
   [:email :string]]
  ```

**Alternatives Considered**:
- Separate mutation schema files → Rejected: Violates single source of truth principle
- Annotation-based approach → Rejected: Not idiomatic Clojure, less declarative
- Convention-based (all entities get all mutations) → Rejected: Too inflexible, security concerns

### 2. Lacinia Mutation Schema Structure

**Question**: How should GraphQL mutations be structured in Lacinia?

**Decision**: Generate standard CRUD mutations with input objects

**Rationale**:
- Follow GraphQL best practices for mutation naming: `createUser`, `updateUser`, `deleteUser`
- Use input object types for complex inputs: `CreateUserInput`, `UpdateUserInput`
- Return the mutated entity (or deletion confirmation) for immediate client updates
- Lacinia mutation schema structure:
  ```clojure
  {:mutations
   {:createUser {:type :User
                 :args {:input {:type :CreateUserInput}}
                 :resolve create-user-resolver}
    :updateUser {:type :User
                 :args {:id {:type '(non-null ID)}
                        :input {:type :UpdateUserInput}}
                 :resolve update-user-resolver}
    :deleteUser {:type :DeleteResult
                 :args {:id {:type '(non-null ID)}}
                 :resolve delete-user-resolver}}}
  ```

**Alternatives Considered**:
- Single generic mutation endpoint → Rejected: Less type-safe, harder to document
- Batch mutation support → Deferred: YAGNI, can add later if needed
- Nested mutation support → Deferred: Start simple, add complexity when justified

### 3. Datomic Transaction Patterns

**Question**: How should GraphQL mutation inputs be converted to Datomic transactions?

**Decision**: Use standard Datomic transaction maps with proper tempids for creates

**Rationale**:
- **Create**: Use `(d/tempid :db.part/user)` for new entities, return resolved tempid
- **Update**: Use entity ID directly, only include changed fields (partial updates)
- **Delete**: Use `:db/retractEntity` for complete entity removal
- Transaction examples:
  ```clojure
  ; Create
  [{:db/id (d/tempid :db.part/user)
    :user/id (java.util.UUID/randomUUID)
    :user/username "alice"
    :user/email "alice@example.com"}]
  
  ; Update (partial)
  [{:db/id [:user/id #uuid "..."]
    :user/email "newemail@example.com"}]
  
  ; Delete
  [[:db/retractEntity [:user/id #uuid "..."]]]
  ```

**Alternatives Considered**:
- CAS (Compare-And-Swap) operations → Deferred: Add when concurrency requirements clarified
- Upsert semantics → Deferred: Start with explicit create/update, add upsert if needed
- Transaction functions → Deferred: Use simple transactions first, add complexity when justified

### 4. Validation Strategy

**Question**: When and how should mutation inputs be validated?

**Decision**: Two-phase validation: Malli schema validation + Datomic constraints

**Rationale**:
- **Phase 1 (Pre-transaction)**: Validate GraphQL inputs against Malli schemas
  - Catches type errors, required fields, format issues
  - Provides clear, immediate feedback to clients
  - Prevents invalid transactions from reaching database
- **Phase 2 (Transaction time)**: Datomic enforces referential integrity, uniqueness
  - Database-level constraints ensure data consistency
  - Handles concurrent modification scenarios
  - Returns transaction errors for constraint violations

**Alternatives Considered**:
- Malli-only validation → Rejected: Doesn't catch referential integrity issues
- Database-only validation → Rejected: Poor error messages, wastes database resources
- Custom validation layer → Rejected: Unnecessary complexity, Malli + Datomic sufficient

### 5. Error Handling Patterns

**Question**: How should mutation errors be communicated to GraphQL clients?

**Decision**: Use Lacinia's error handling with structured error maps

**Rationale**:
- Return errors in GraphQL response `errors` array (standard GraphQL pattern)
- Structure: `{:message "..." :extensions {:code :VALIDATION_ERROR :field :email}}`
- Error categories:
  - `:VALIDATION_ERROR` - Malli schema validation failures
  - `:CONSTRAINT_VIOLATION` - Datomic uniqueness/integrity violations
  - `:NOT_FOUND` - Entity doesn't exist for update/delete
  - `:TRANSACTION_FAILED` - Database transaction errors
- Successful mutations return data in `data` field, errors in `errors` field

**Alternatives Considered**:
- Exception-based error handling → Rejected: Not idiomatic Clojure, harder to test
- Custom error response format → Rejected: Violates GraphQL spec
- Silent failures → Rejected: Poor developer experience

### 6. Relationship Handling in Mutations

**Question**: How should mutations handle entity relationships?

**Decision**: Support relationship references by ID, defer nested creates

**Rationale**:
- **Phase 1 (this feature)**: Accept entity IDs for relationships
  - Example: `createPost(input: {authorId: "uuid", title: "..."})`
  - Simple, explicit, easy to validate
- **Future enhancement**: Nested creates (e.g., create user with posts in single mutation)
  - Deferred per YAGNI principle
  - Can add when use case emerges

**Alternatives Considered**:
- Nested creates from start → Rejected: Premature complexity, unclear requirements
- Relationship-only mutations → Deferred: Add if needed for many-to-many relationships
- Automatic relationship resolution → Rejected: Too magical, hard to debug

## Technology Best Practices

### Lacinia Mutations
- Use input object types for all non-trivial mutations
- Return the full mutated entity for client cache updates
- Provide clear resolver functions with descriptive names
- Document mutation side effects in schema descriptions

### Datomic Transactions
- Always use lookup refs `[:attr value]` instead of numeric entity IDs
- Batch related operations in single transaction for atomicity
- Use `d/resolve-tempid` to get actual entity IDs after transaction
- Handle transaction errors gracefully with clear messages

### Malli Validation
- Reuse entity schemas for input validation (DRY principle)
- Use `:optional` keys for update inputs (partial updates)
- Provide custom error messages for common validation failures
- Validate early (before transaction) for better error messages

## Implementation Patterns

### Mutation Parser Pattern
```clojure
(defn parse-mutations
  "Extract mutation definitions from entity schema properties"
  [entity-type schema]
  (let [props (m/properties schema)
        mutations (:ringline/mutations props #{})]
    {:entity-type entity-type
     :operations mutations
     :input-schema (derive-input-schema schema mutations)}))
```

### Transaction Builder Pattern
```clojure
(defn build-create-transaction
  "Convert GraphQL create input to Datomic transaction"
  [entity-type input-data parsed-schema]
  (let [tempid (d/tempid :db.part/user)
        attrs (input->datomic-attrs entity-type input-data parsed-schema)]
    [(assoc attrs :db/id tempid)]))
```

### Resolver Pattern
```clojure
(defn create-mutation-resolver
  "Create a Lacinia resolver for entity creation"
  [entity-type db-conn parsed-schema]
  (fn [context args value]
    (let [input (:input args)
          validation (m/explain (get-input-schema parsed-schema :create) input)]
      (if validation
        {:errors [(validation-error->graphql validation)]}
        (let [tx (build-create-transaction entity-type input parsed-schema)
              result @(d/transact db-conn tx)]
          {:data (transform-entity (resolve-created-entity result))})))))
```

## Summary

All research questions resolved with clear decisions based on:
- Existing Ringline framework patterns (consistency)
- GraphQL and Datomic best practices (industry standards)
- Constitutional principles (simplicity, data-driven design, TDD)
- YAGNI principle (defer complexity until needed)

**Status**: ✅ Ready for Phase 1 (Design & Contracts)

