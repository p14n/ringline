# Data Model: Custom Query and Mutation Schema Support

**Feature**: 004-custom-schema-resolvers  
**Date**: 2026-01-25  
**Purpose**: Define data structures for custom operation definitions

## Core Entities

### CustomQueryDefinition

Represents a custom GraphQL query defined via Malli schema properties.

**Malli Schema**:
```clojure
(def CustomQueryDefinition
  "Schema for a custom query definition extracted from Malli schema properties.
   
   Contains the query name, argument schema, and return type."
  [:map
   [:name :keyword]                    ; GraphQL query name (e.g., :searchUsers)
   [:args :any]                        ; Malli schema for query arguments
   [:return-type :keyword]             ; Return type reference (e.g., :User, :string)
   [:description {:optional true} :string]])  ; Optional GraphQL description
```

**Fields**:
- `name`: Keyword representing the GraphQL query name (e.g., `:searchUsers`, `:findByEmail`)
- `args`: Malli schema defining the query's input arguments (e.g., `[:map [:query :string] [:limit :int]]`)
- `return-type`: Keyword reference to the return type (entity name like `:User`, or primitive like `:string`)
- `description`: Optional human-readable description for GraphQL schema documentation

**Validation Rules**:
- `name` must be a valid keyword
- `args` must be a valid Malli schema (validated during parsing)
- `return-type` must reference an existing entity type or primitive type
- If `description` is provided, it must be a non-empty string

**Example**:
```clojure
{:name :searchUsers
 :args [:map
        [:query :string]
        [:limit {:optional true} :int]]
 :return-type :User
 :description "Search users by query string"}
```

### CustomMutationDefinition

Represents a custom GraphQL mutation defined via Malli schema properties.

**Malli Schema**:
```clojure
(def CustomMutationDefinition
  "Schema for a custom mutation definition extracted from Malli schema properties.
   
   Contains the mutation name, input schema, and return type."
  [:map
   [:name :keyword]                    ; GraphQL mutation name (e.g., :approveOrder)
   [:args :any]                        ; Malli schema for mutation input
   [:return-type :keyword]             ; Return type reference (e.g., :Order, :boolean)
   [:description {:optional true} :string]])  ; Optional GraphQL description
```

**Fields**:
- `name`: Keyword representing the GraphQL mutation name (e.g., `:approveOrder`, `:transferOwnership`)
- `args`: Malli schema defining the mutation's input arguments (e.g., `[:map [:order-id :uuid] [:notes :string]]`)
- `return-type`: Keyword reference to the return type (entity name like `:Order`, or primitive like `:boolean`)
- `description`: Optional human-readable description for GraphQL schema documentation

**Validation Rules**:
- `name` must be a valid keyword
- `args` must be a valid Malli schema (validated during parsing)
- `return-type` must reference an existing entity type or primitive type
- If `description` is provided, it must be a non-empty string

**Example**:
```clojure
{:name :approveOrder
 :args [:map
        [:order-id :uuid]
        [:approver-notes {:optional true} :string]]
 :return-type :Order
 :description "Approve an order with optional notes"}
```

### ResolverMap

Represents the mapping between custom operation names and their resolver functions.

**Structure**:
```clojure
;; Map of operation name (keyword) to resolver function
{:searchUsers (fn [context args value] ...)
 :approveOrder (fn [context args value] ...)}
```

**Fields**:
- Key: Operation name (keyword) matching the `:name` field in CustomQueryDefinition or CustomMutationDefinition
- Value: Lacinia resolver function with signature `(fn [context args value] ...)`

**Validation Rules**:
- All custom query names must have corresponding entries in ResolverMap
- All custom mutation names must have corresponding entries in ResolverMap
- Resolver functions must accept 3 arguments (Lacinia standard signature)

**Example**:
```clojure
{:searchUsers
 (fn [context args value]
   (let [db-conn (:db-conn context)
         query-str (:query args)
         limit (or (:limit args) 10)]
     ;; Custom search logic here
     ))
 
 :approveOrder
 (fn [context args value]
   (let [db-conn (:db-conn context)
         order-id (:order-id args)
         notes (:approver-notes args)]
     ;; Custom approval logic here
     ))}
```

## Data Flow

### 1. Schema Definition (Developer Input)

Developer defines custom operations using Malli schema properties:

```clojure
(def user-schema
  [:map {:ringline/datomic-ns "user"
         :ringline/query-root true
         :ringline/custom-query {:name :searchUsers
                                 :args [:map [:query :string]]
                                 :return-type :User}}
   [:id :uuid]
   [:email :string]
   [:username :string]])
```

### 2. Parsing (Framework Processing)

`ringline.schema.parser/parse-schema` extracts custom operation definitions:

```clojure
;; Input: Malli schema with :ringline/custom-query property
;; Output: ParsedSchema with :custom-queries field
{:schema-name :user
 :fields [...]
 :properties {...}
 :custom-queries [{:name :searchUsers
                   :args [:map [:query :string]]
                   :return-type :User}]}
```

### 3. Schema Generation (Framework Processing)

`ringline.schema.lacinia/generate-schema` creates Lacinia schema from custom operations:

```clojure
;; Input: CustomQueryDefinition
;; Output: Lacinia query schema
{:searchUsers
 {:type :User
  :args {:query {:type 'String}}
  :resolve :custom-resolver-placeholder}}
```

### 4. Resolver Attachment (Developer Input + Framework Validation)

Developer provides resolver map to `init-framework`:

```clojure
(def resolvers
  {:searchUsers (fn [context args value] ...)})

(init-framework schemas {:custom-resolvers resolvers})
```

Framework validates all custom operations have resolvers, then attaches them to Lacinia schema.

## State Transitions

Custom operations are stateless - they are defined once during framework initialization and remain immutable throughout the application lifecycle.

**Lifecycle**:
1. **Definition**: Developer defines custom operations in Malli schema properties
2. **Parsing**: Framework extracts CustomQueryDefinition / CustomMutationDefinition
3. **Validation**: Framework validates structure and resolver attachment
4. **Generation**: Framework generates Lacinia schema entries
5. **Execution**: Lacinia invokes attached resolvers when queries/mutations are executed

No state changes occur after initialization. All data structures are immutable.

