# Data Model: GraphQL Mutations

**Feature**: 002-graphql-mutations  
**Date**: 2026-01-24  
**Source**: Extracted from [spec.md](spec.md) Requirements section

## Overview

This document defines the data entities and their relationships for the mutation support feature. All entities are represented as immutable Clojure maps with Malli schemas for validation.

## Core Entities

### 1. Mutation Definition

**Purpose**: Represents a mutation operation (create/update/delete) for an entity

**Fields**:
- `:entity-type` (keyword, required) - The entity this mutation operates on (e.g., `:user`, `:post`)
- `:operation` (keyword, required) - The mutation type: `:create`, `:update`, or `:delete`
- `:input-schema` (Malli schema, required) - Schema for validating mutation inputs
- `:required-fields` (vector of keywords, optional) - Fields required for this operation
- `:optional-fields` (vector of keywords, optional) - Fields that can be provided but aren't required

**Malli Schema**:
```clojure
(def MutationDefinition
  [:map
   [:entity-type :keyword]
   [:operation [:enum :create :update :delete]]
   [:input-schema :any]  ; Malli schema object
   [:required-fields {:optional true} [:vector :keyword]]
   [:optional-fields {:optional true} [:vector :keyword]]])
```

**Example**:
```clojure
{:entity-type :user
 :operation :create
 :input-schema [:map
                [:username :string]
                [:email :string]
                [:created-at {:optional true} :int]]
 :required-fields [:username :email]
 :optional-fields [:created-at]}
```

**Relationships**: 
- Derived from entity schemas (parsed by `ringline.mutation.parser`)
- Used by Lacinia schema generator to create mutation fields
- Used by transaction converter to validate inputs

**State Transitions**: Immutable - created during framework initialization

---

### 2. Mutation Input

**Purpose**: The data provided by the client for a mutation operation

**Fields**:
- `:operation` (keyword, required) - The mutation type: `:create`, `:update`, or `:delete`
- `:entity-type` (keyword, required) - The target entity type
- `:entity-id` (UUID, required for update/delete) - The entity identifier
- `:data` (map, required for create/update) - The field values to set
- `:timestamp` (int, optional) - When the mutation was requested (epoch milliseconds)

**Malli Schema**:
```clojure
(def MutationInput
  [:map
   [:operation [:enum :create :update :delete]]
   [:entity-type :keyword]
   [:entity-id {:optional true} :uuid]  ; Required for update/delete
   [:data {:optional true} :map]        ; Required for create/update
   [:timestamp {:optional true} :int]])
```

**Example (Create)**:
```clojure
{:operation :create
 :entity-type :user
 :data {:username "alice"
        :email "alice@example.com"
        :created-at 1706140800000}}
```

**Example (Update)**:
```clojure
{:operation :update
 :entity-type :user
 :entity-id #uuid "550e8400-e29b-41d4-a716-446655440000"
 :data {:email "newemail@example.com"}}
```

**Example (Delete)**:
```clojure
{:operation :delete
 :entity-type :user
 :entity-id #uuid "550e8400-e29b-41d4-a716-446655440000"}
```

**Relationships**:
- Validated against MutationDefinition's input-schema
- Converted to Datomic transaction data by `ringline.mutation.transaction`
- Received from GraphQL resolver arguments

**Validation Rules**:
- Must match the entity's Malli schema
- For update: entity-id must exist in database
- For create: must not violate uniqueness constraints
- For delete: entity-id must exist in database

---

### 3. Mutation Result

**Purpose**: The outcome of a mutation operation

**Fields**:
- `:success` (boolean, required) - Whether the mutation succeeded
- `:operation` (keyword, required) - The mutation type that was executed
- `:entity-type` (keyword, required) - The entity type that was mutated
- `:data` (map, optional) - The mutated entity data (for successful create/update)
- `:entity-id` (UUID, optional) - The ID of the mutated/deleted entity
- `:errors` (vector of maps, optional) - Error details if mutation failed
- `:timestamp` (int, required) - When the mutation completed (epoch milliseconds)

**Malli Schema**:
```clojure
(def MutationResult
  [:map
   [:success :boolean]
   [:operation [:enum :create :update :delete]]
   [:entity-type :keyword]
   [:data {:optional true} :map]
   [:entity-id {:optional true} :uuid]
   [:errors {:optional true} [:vector :map]]
   [:timestamp :int]])
```

**Example (Successful Create)**:
```clojure
{:success true
 :operation :create
 :entity-type :user
 :data {:id #uuid "550e8400-e29b-41d4-a716-446655440000"
        :username "alice"
        :email "alice@example.com"
        :created-at 1706140800000}
 :entity-id #uuid "550e8400-e29b-41d4-a716-446655440000"
 :timestamp 1706140801000}
```

**Example (Failed Validation)**:
```clojure
{:success false
 :operation :create
 :entity-type :user
 :errors [{:code :VALIDATION_ERROR
           :message "Invalid email format"
           :field :email
           :value "not-an-email"}]
 :timestamp 1706140801000}
```

**Relationships**:
- Returned by `ringline.mutation.executor`
- Transformed to GraphQL response format by response transformer
- Contains entity data from Datomic transaction result

**State Transitions**: Immutable - created once per mutation execution

## Entity Relationships

```
┌─────────────────────┐
│ Entity Schema       │
│ (Malli)             │
└──────────┬──────────┘
           │ parsed by
           ▼
┌─────────────────────┐
│ Mutation Definition │
│                     │
└──────────┬──────────┘
           │ validates
           ▼
┌─────────────────────┐      converted to      ┌─────────────────────┐
│ Mutation Input      │─────────────────────▶│ Datomic Transaction │
│                     │                       │                     │
└─────────────────────┘                       └──────────┬──────────┘
                                                         │ executed
                                                         ▼
                                              ┌─────────────────────┐
                                              │ Mutation Result     │
                                              │                     │
                                              └─────────────────────┘
```

## Validation Rules Summary

### Cross-Entity Validation
- Mutation inputs must conform to entity's Malli schema
- Update/delete operations require existing entity-id
- Create operations must not violate uniqueness constraints
- Relationship references must point to existing entities

### Integrity Constraints
- Entity IDs must be valid UUIDs
- Required fields must be present for create operations
- Partial updates only modify specified fields
- Delete operations check for referential integrity (handled by Datomic)

## Notes

- All entities are immutable - mutations create new versions in Datomic
- Timestamps use epoch milliseconds (`:int` type) for consistency with existing framework
- Error maps follow GraphQL error extension pattern for client compatibility
- Entity data in results uses same format as query responses (transformed by existing `ringline.response.transformer`)

