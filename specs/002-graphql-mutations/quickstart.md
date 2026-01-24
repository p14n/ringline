# Quick Start: GraphQL Mutations

**Feature**: 002-graphql-mutations  
**Audience**: Developers using the Ringline framework  
**Prerequisites**: Completed feature 001 (Malli-GraphQL Framework)

## Overview

This guide shows how to add mutation support to your Ringline application, enabling create, update, and delete operations through GraphQL.

## Step 1: Define Mutations in Your Schema

Add the `:ringline/mutations` property to your entity schemas to specify which operations are allowed:

```clojure
(require '[malli.core :as m])

;; Define User entity with mutations
(def user-schema
  [:map
   {:ringline/datomic-ns :user
    :ringline/query-root true
    :ringline/searchable [:email :username]
    :ringline/mutations #{:create :update :delete}}  ; NEW: Enable all mutations
   [:id :uuid]
   [:username :string]
   [:email :string]
   [:created-at :int]])

;; Define Post entity with limited mutations
(def post-schema
  [:map
   {:ringline/datomic-ns :post
    :ringline/query-root true
    :ringline/searchable [:title]
    :ringline/mutations #{:create :update}}  ; NEW: Only create and update, no delete
   [:id :uuid]
   [:title :string]
   [:content :string]
   [:published? :boolean]
   [:created-at :int]
   [:author {:ringline/ref-to :user} :uuid]])

(def schemas {:user user-schema :post post-schema})
```

## Step 2: Initialize Framework with Mutations

Use the updated `init-framework` function which now includes mutation support:

```clojure
(require '[ringline.core :as core])

;; Initialize framework - now generates mutations too
(def framework (core/init-framework schemas {}))

;; Framework now contains:
;; - :datomic - Datomic schema (same as before)
;; - :lacinia - Lacinia schema with queries AND mutations (NEW)
;; - :parsed - Parsed schemas with mutation definitions (NEW)
```

## Step 3: Set Up GraphQL with Mutations

The Lacinia schema now includes mutations:

```clojure
(require '[com.walmartlabs.lacinia :as lacinia]
         '[com.walmartlabs.lacinia.schema :as schema])

;; Attach resolvers (queries and mutations)
(def schema-with-resolvers
  (-> (:lacinia framework)
      (assoc-in [:mutations :createUser :resolve] 
                (core/create-mutation-resolver :user :create db-conn (:parsed framework)))
      (assoc-in [:mutations :updateUser :resolve]
                (core/create-mutation-resolver :user :update db-conn (:parsed framework)))
      (assoc-in [:mutations :deleteUser :resolve]
                (core/create-mutation-resolver :user :delete db-conn (:parsed framework)))
      (assoc-in [:mutations :createPost :resolve]
                (core/create-mutation-resolver :post :create db-conn (:parsed framework)))
      (assoc-in [:mutations :updatePost :resolve]
                (core/create-mutation-resolver :post :update db-conn (:parsed framework)))
      schema/compile))
```

## Step 4: Execute Mutations

### Create a User

```graphql
mutation {
  createUser(input: {
    username: "alice"
    email: "alice@example.com"
  }) {
    id
    username
    email
    createdAt
  }
}
```

Response:
```json
{
  "data": {
    "createUser": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "alice",
      "email": "alice@example.com",
      "createdAt": 1706140800000
    }
  }
}
```

### Update a User

```graphql
mutation {
  updateUser(
    id: "550e8400-e29b-41d4-a716-446655440000"
    input: {
      email: "newemail@example.com"
    }
  ) {
    id
    username
    email
  }
}
```

### Delete a User

```graphql
mutation {
  deleteUser(id: "550e8400-e29b-41d4-a716-446655440000") {
    success
    id
  }
}
```

## Step 5: Handle Validation Errors

When validation fails, errors are returned in the GraphQL response:

```graphql
mutation {
  createUser(input: {
    username: "alice"
    email: "not-an-email"  # Invalid email format
  }) {
    id
    username
  }
}
```

Response:
```json
{
  "data": null,
  "errors": [
    {
      "message": "Invalid email format",
      "extensions": {
        "code": "VALIDATION_ERROR",
        "field": "email",
        "value": "not-an-email"
      }
    }
  ]
}
```

## Complete Example

```clojure
(ns myapp.core
  (:require [ringline.core :as core]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [datomic.api :as d]))

;; 1. Define schemas with mutations
(def schemas
  {:user [:map
          {:ringline/datomic-ns :user
           :ringline/query-root true
           :ringline/mutations #{:create :update :delete}}
          [:id :uuid]
          [:username :string]
          [:email :string]]})

;; 2. Initialize framework
(def framework (core/init-framework schemas {}))

;; 3. Set up Datomic
(def db-uri "datomic:mem://myapp")
(d/create-database db-uri)
(def conn (d/connect db-uri))
@(d/transact conn (:datomic framework))

;; 4. Attach mutation resolvers
(def schema
  (-> (:lacinia framework)
      (core/attach-mutation-resolvers conn (:parsed framework))
      schema/compile))

;; 5. Execute mutations
(lacinia/execute schema
  "mutation { createUser(input: {username: \"alice\", email: \"alice@example.com\"}) { id username } }"
  nil nil)
```

## Next Steps

- Add custom validation rules to your Malli schemas
- Implement authorization checks in mutation resolvers
- Add optimistic concurrency control for updates
- Explore batch mutations for multiple operations

## Troubleshooting

**Mutation not appearing in schema**: Check that `:ringline/mutations` property is set on the entity schema

**Validation errors**: Ensure input data matches the Malli schema field types and constraints

**Transaction errors**: Check Datomic logs for constraint violations or referential integrity issues

**Entity not found**: Verify the entity ID exists in the database before update/delete operations

