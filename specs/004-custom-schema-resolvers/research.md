# Research: Custom Query and Mutation Schema Support

**Feature**: 004-custom-schema-resolvers  
**Date**: 2026-01-25  
**Purpose**: Resolve technical unknowns and establish implementation patterns

## Research Questions

### 1. How to extend existing parser to recognize custom operation properties?

**Decision**: Add new property keywords to `ringline.schema.properties` and extend `parse-schema` function in `ringline.schema.parser`.

**Rationale**:
- Existing framework already uses custom Malli properties (`:ringline/query-root`, `:ringline/mutations`, `:ringline/searchable`)
- Parser already extracts properties from schema metadata
- Consistent with established pattern: define property keywords in `properties.clj`, use in `parser.clj`

**Implementation Pattern**:
```clojure
;; In ringline.schema.properties
(def custom-query
  "Property key for defining custom GraphQL queries.
   Example: {:ringline/custom-query {:name :searchUsers
                                      :args [:map [:query :string]]
                                      :return-type :User}}"
  :ringline/custom-query)

(def custom-mutation
  "Property key for defining custom GraphQL mutations.
   Example: {:ringline/custom-mutation {:name :approveOrder
                                         :args [:map [:order-id :uuid]]
                                         :return-type :Order}}"
  :ringline/custom-mutation)
```

**Alternatives Considered**:
- Separate configuration map passed to `init-framework` → Rejected: Breaks single-source-of-truth principle
- Special schema wrapper type → Rejected: Adds unnecessary abstraction
- Dedicated namespace functions → Rejected: Less declarative than schema properties

### 2. How to merge custom operations with auto-generated ones in Lacinia schema?

**Decision**: Extend `generate-schema` in `ringline.schema.lacinia` to process custom operations and use simple map merge with custom operations taking precedence.

**Rationale**:
- Lacinia schemas are just Clojure maps with `:queries` and `:mutations` keys
- Map merge with custom operations last ensures they override auto-generated ones
- Existing `generate-schema` already merges multiple entity schemas
- Simple, predictable behavior: `(merge auto-generated-ops custom-ops)`

**Implementation Pattern**:
```clojure
;; In ringline.schema.lacinia
(defn generate-custom-query-schema
  "Generate Lacinia query schema from custom query definition"
  [custom-query-def]
  {(:name custom-query-def)
   {:type (:return-type custom-query-def)
    :args (malli->lacinia-args (:args custom-query-def))
    :resolve :custom-resolver-placeholder}})

(defn merge-schemas
  "Merge auto-generated and custom operations, custom takes precedence"
  [auto-generated custom-operations]
  {:queries (merge (:queries auto-generated)
                   (:queries custom-operations))
   :mutations (merge (:mutations auto-generated)
                     (:mutations custom-operations))})
```

**Alternatives Considered**:
- Throw error on conflicts → Rejected: Too restrictive, users want override capability
- Namespace-based separation → Rejected: Adds complexity, users want simple override
- Priority system → Rejected: YAGNI, simple merge is sufficient

### 3. How to validate resolver attachment during initialization?

**Decision**: Add validation step in `init-framework` that checks all custom operations have corresponding resolvers in the resolver map.

**Rationale**:
- Fail-fast principle: catch configuration errors at startup, not at query time
- Simple validation: check that every custom operation name exists in resolver map
- Existing `init-framework` already performs validation (schema parsing, etc.)
- Clear error messages guide developers to fix configuration

**Implementation Pattern**:
```clojure
;; In ringline.core
(defn validate-custom-resolvers
  "Validate that all custom operations have attached resolvers.
   Throws ex-info if any custom operation is missing a resolver."
  [custom-operations resolver-map]
  (let [custom-op-names (set (concat (keys (:queries custom-operations))
                                     (keys (:mutations custom-operations))))
        resolver-names (set (keys resolver-map))
        missing (clojure.set/difference custom-op-names resolver-names)]
    (when (seq missing)
      (throw (ex-info "Custom operations missing resolvers"
                      {:missing-resolvers missing
                       :custom-operations custom-op-names
                       :provided-resolvers resolver-names})))))
```

**Alternatives Considered**:
- Runtime validation on first query → Rejected: Fails in production, not during development
- Default no-op resolvers → Rejected: Hides configuration errors
- Optional resolvers with warnings → Rejected: Spec requires fail-fast validation

### 4. Best practices for resolver function signatures in Lacinia?

**Decision**: Follow Lacinia's standard resolver signature: `(fn [context args value])`.

**Rationale**:
- Lacinia documentation specifies this signature for all resolvers
- Existing auto-generated resolvers already use this pattern
- `context` provides access to database connection and other framework state
- `args` contains GraphQL query arguments
- `value` contains parent object (for nested resolvers)

**Reference**: Lacinia resolver signature from existing `ringline.core/create-resolver`:
```clojure
(defn create-resolver
  [entity-type db-conn parsed-schema]
  (fn [context args value]
    ;; resolver implementation
    ))
```

**Alternatives Considered**: None - this is the Lacinia standard.

### 5. How to handle type references (`:User` -> GraphQL User type)?

**Decision**: Use keyword-based type references that match entity schema names, resolve during Lacinia schema generation.

**Rationale**:
- Existing framework already uses keywords for entity types (`:User`, `:Post`)
- Lacinia schemas reference types by keyword
- Simple resolution: if type is keyword and matches entity name, it's a valid reference
- Validation during schema generation catches invalid type references

**Implementation Pattern**:
```clojure
;; Type reference resolution
(defn resolve-type-reference
  "Resolve a type reference keyword to Lacinia type.
   If keyword matches entity name, return as-is.
   If keyword is primitive (:string, :int), return as-is.
   Otherwise, throw validation error."
  [type-ref entity-names]
  (cond
    (contains? entity-names type-ref) type-ref
    (contains? #{:string :int :float :boolean :ID} type-ref) type-ref
    :else (throw (ex-info "Invalid type reference"
                          {:type-ref type-ref
                           :valid-entities entity-names}))))
```

**Alternatives Considered**:
- String references → Rejected: Keywords are more idiomatic in Clojure
- Fully-qualified namespaces → Rejected: Verbose, doesn't match existing patterns
- Explicit type registration → Rejected: Adds unnecessary ceremony

## Summary

All technical unknowns have been resolved. The implementation will:

1. **Extend existing patterns**: Add custom properties following established `:ringline/*` convention
2. **Reuse existing infrastructure**: Leverage current parsing and schema generation functions
3. **Simple merging**: Use map merge for combining auto-generated and custom operations
4. **Fail-fast validation**: Check resolver attachment during `init-framework`
5. **Standard Lacinia patterns**: Follow Lacinia resolver signatures and type references

No new dependencies required. No architectural changes needed. Implementation extends existing namespaces with minimal, focused additions.

