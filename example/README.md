# User CRUD Example

This example demonstrates the Ringline framework with a simple User entity that supports full CRUD operations.

## Overview

The Ringline framework uses **Malli schemas as a single source of truth** to automatically generate:
- Datomic database schemas
- GraphQL (Lacinia) schemas with queries and mutations
- Validation logic
- Type conversions

## Running the Example

### Option 1: HTTP GraphQL Server (Recommended)

Start the HTTP server with Ring, Reitit, and Jetty:

```bash
# From the project root
clojure -M:example
```

This starts a GraphQL server on `http://localhost:3000` with:
- **GraphQL endpoint**: `http://localhost:3000/graphql` - POST GraphQL queries here
- **GraphiQL UI**: `http://localhost:3000/graphiql` - Interactive GraphQL playground in your browser
- **Health check**: `http://localhost:3000/health` - Server health status

Open `http://localhost:3000/graphiql` in your browser to interactively explore the GraphQL API!

### Option 2: Run Tests

```bash
# Run example tests only
clojure -M:test --focus :example

# Run all framework tests (including example)
clojure -M:test
```

### Option 3: REPL Exploration

See the comment block in `example/src/starwars/core.clj` for REPL examples.

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

## Using the HTTP Server

When you run `clojure -M:example`, the server starts and you'll see:

```
Database created successfully
Datomic schema installed successfully
Database and schema initialized

=== Ringline GraphQL Server Started ===
GraphQL endpoint: http://localhost:3000/graphql
GraphiQL UI:      http://localhost:3000/graphiql
Health check:     http://localhost:3000/health

Press Ctrl+C to stop the server
```

### Using GraphiQL

1. Open `http://localhost:3000/graphiql` in your browser
2. Try the example queries and mutations listed above
3. Use the "Docs" panel to explore the auto-generated schema

### Using curl

You can also query the GraphQL endpoint directly:

```bash
# Query by email
curl -X POST http://localhost:3000/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ user(email: \"alice@example.com\") { id name email age } }"}'

# Create user mutation
curl -X POST http://localhost:3000/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "mutation { createUser(input: { name: \"Diana Prince\", email: \"diana@example.com\", age: 28 }) { id name email age } }"}'
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

