# User CRUD Example

This example demonstrates the Ringline framework with a simple User entity that supports full CRUD operations.

## Overview

The Ringline framework uses **Malli schemas as a single source of truth** to automatically generate:
- Datomic database schemas
- GraphQL (Lacinia) schemas with queries and mutations
- Validation logic
- Type conversions

## Running the Example

```bash
# From the project root
clojure -M:example
```

## What This Example Demonstrates

### 1. Malli Schema Definition

The User schema in `example/src/starwars/schema.clj` defines:

```clojure
(def user-schema
  [:map
   {:ringline/datomic-ns "user"
    :ringline/query-root true
    :ringline/searchable [:email :name]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:email :string]
   [:age {:optional true} :int]])
```

**Key properties:**
- `:ringline/datomic-ns` - Namespace for Datomic attributes (`:user/id`, `:user/name`, etc.)
- `:ringline/query-root` - Makes this entity queryable via GraphQL
- `:ringline/searchable` - Fields that can be used as query arguments
- `:ringline/mutations` - Enables CRUD operations (create, update, delete)

### 2. Automatic GraphQL Schema Generation

From the Malli schema above, Ringline automatically generates:

**Queries:**
```graphql
type Query {
  user(email: String, name: String): User
}
```

**Mutations:**
```graphql
type Mutation {
  createUser(input: CreateUserInput!): User
  updateUser(input: UpdateUserInput!): User
  deleteUser(input: DeleteUserInput!): Boolean
}

input CreateUserInput {
  name: String!
  email: String!
  age: Int
}

input UpdateUserInput {
  id: ID!
  name: String
  email: String
  age: Int
}

input DeleteUserInput {
  id: ID!
}
```

**Types:**
```graphql
type User {
  id: ID!
  name: String!
  email: String!
  age: Int
}
```

### 3. Example Queries and Mutations

The example runs the following operations:

**Query by email:**
```graphql
{
  user(email: "alice@example.com") {
    id
    name
    email
    age
  }
}
```

**Query by name:**
```graphql
{
  user(name: "Bob Smith") {
    id
    name
    email
    age
  }
}
```

**Create mutation:**
```graphql
mutation {
  createUser(input: {
    name: "Diana Prince"
    email: "diana@example.com"
    age: 28
  }) {
    id
    name
    email
    age
  }
}
```

**Update mutation:**
```graphql
mutation {
  updateUser(input: {
    id: "00000000-0000-0000-0000-000000000002"
    age: 26
  }) {
    id
    name
    email
    age
  }
}
```



## Key Concepts

### Single Source of Truth

The Malli schema is the **only** place where you define your data model. Everything else is generated:
- No need to write separate GraphQL schema files
- No need to write Datomic schema files
- No need to write validation logic
- No need to write mutation resolvers

### Automatic CRUD

By adding `:ringline/mutations #{:create :update :delete}` to your schema, you get:
- Fully functional create, update, and delete mutations
- Input validation using Malli
- Proper error handling
- Transaction management

### Type Safety

Ringline handles type conversions automatically:
- Malli `:uuid` ↔ GraphQL `ID` (as string)
- Malli `:int` ↔ GraphQL `Int`
- Malli `:string` ↔ GraphQL `String`
- Malli `:double` ↔ GraphQL `Float`

### Searchable Fields

The `:ringline/searchable [:email :name]` property generates query arguments:
- `user(email: "alice@example.com")` - Search by email
- `user(name: "Bob Smith")` - Search by name

## Sample Data

The example includes three sample users:
- **Alice Johnson** (alice@example.com, age 30)
- **Bob Smith** (bob@example.com, age 25)
- **Charlie Brown** (charlie@example.com, age 35)

## Expected Output

When you run `clojure -M:example`, you should see:

```
=== Example Query: Search User by Email ===
{:data {:user {:id "...", :name "Alice Johnson", :email "alice@example.com", :age 30}}}

=== Example Mutation: Create New User ===
{:data {:createUser {:id "...", :name "Diana Prince", :email "diana@example.com", :age 28}}}

=== Example Mutation: Update User ===
{:data {:updateUser {:id "...", :name nil, :email nil, :age 26}}}

=== Verify Update: Query Updated User ===
{:data {:user {:id "...", :name "Bob Smith", :email "bob@example.com", :age 26}}}

=== Example Mutation: Delete User ===
{:data {:deleteUser true}}
```

## Next Steps

To use Ringline in your own project:

1. **Define your Malli schemas** with Ringline properties:
   - `:ringline/datomic-ns` for database namespace
   - `:ringline/query-root` to enable queries
   - `:ringline/searchable` for query arguments
   - `:ringline/mutations` for CRUD operations

2. **Initialize the framework:**
   ```clojure
   (ringline/init-framework schemas {})
   ```

3. **Attach resolvers** to the generated Lacinia schema

4. **Compile and use** the schema with Lacinia

See the code in `example/src/starwars/core.clj` for a complete working example.

