# Research Summary: Malli Type Support for Custom Scalars

**Date**: 2026-01-24  
**Status**: Complete  
**Files**: 
- Detailed findings: `research-findings.md`
- Code examples: `malli-type-examples.clj`

---

## Quick Answers

| Question | Answer | Malli Support |
|----------|--------|---------------|
| **Date-only type?** | ✅ YES | `:time/local-date` (experimental) |
| **DateTime with timezone?** | ✅ YES | `:time/offset-date-time` (experimental) |
| **BigDecimal/Decimal?** | ⚠️ NO (built-in) | Custom schema needed |
| **Enum type?** | ✅ YES | `:enum` (built-in) |

---

## Recommended Approach

### 1. Date Type → `:time/local-date`

**Source**: `malli.experimental.time`

```clojure
;; Schema
[:birth-date :time/local-date]

;; Example value
(LocalDate/parse "2024-01-24")

;; String format
"2024-01-24"  ; ISO 8601, 10 characters

;; Datomic mapping
:db.type/instant

;; GraphQL mapping
Date (custom scalar)
```

### 2. DateTime Type → `:time/offset-date-time`

**Source**: `malli.experimental.time`

```clojure
;; Schema
[:created-at :time/offset-date-time]

;; Example value
(OffsetDateTime/parse "2024-01-24T10:30:00-05:00")

;; String format
"2024-01-24T10:30:00-05:00"  ; ISO 8601, ~25 characters

;; Datomic mapping
:db.type/instant

;; GraphQL mapping
DateTime (custom scalar)
```

### 3. Decimal Type → `:decimal` (Custom)

**Source**: Custom implementation (see `malli-type-examples.clj`)

```clojure
;; Schema
[:price [:decimal {:scale 2}]]

;; Example value
(BigDecimal. "99.99")

;; String format
"99.99"

;; Datomic mapping
:db.type/bigdec

;; GraphQL mapping
Decimal (custom scalar)
```

### 4. Enum Type → `:enum`

**Source**: Malli built-in

```clojure
;; Schema
[:status [:enum :draft :published :archived]]

;; Example value
:published

;; String format
"published" (or keyword :published)

;; Datomic mapping
:db.type/keyword

;; GraphQL mapping
Status (enum type with values: DRAFT, PUBLISHED, ARCHIVED)
```

---

## Implementation Checklist

### Phase 1: Add Time Schema Support

- [ ] Add `malli.experimental.time` dependency (already in Malli 0.20.0)
- [ ] Update registry to include `(met/schemas)`
- [ ] Add `:time/local-date` to type mappings
- [ ] Add `:time/offset-date-time` to type mappings
- [ ] Create Lacinia custom scalars for Date and DateTime
- [ ] Add transformation support (string ↔ temporal types)

### Phase 2: Add Decimal Support

- [ ] Implement custom `:decimal` schema (see examples)
- [ ] Add `:decimal` to type mappings
- [ ] Map to `:db.type/bigdec` (Datomic)
- [ ] Create Lacinia custom scalar for Decimal
- [ ] Add transformation support (string ↔ BigDecimal)

### Phase 3: Enhance Enum Support

- [ ] Update parser to extract enum values
- [ ] Generate Lacinia enum types from Malli `:enum` schemas
- [ ] Map enum keywords to uppercase GraphQL enum values
- [ ] Add enum validation in mutations

### Phase 4: Testing

- [ ] Unit tests for each scalar type
- [ ] Integration tests with Datomic
- [ ] Integration tests with GraphQL
- [ ] Transformation tests (encode/decode)

---

## Type Mapping Tables

### Malli → Datomic

| Malli Type | Datomic Type | Notes |
|------------|--------------|-------|
| `:time/local-date` | `:db.type/instant` | Store as instant, validate format |
| `:time/offset-date-time` | `:db.type/instant` | Preserves timezone in serialization |
| `:decimal` | `:db.type/bigdec` | Arbitrary precision |
| `:enum` | `:db.type/keyword` | Store as keyword |

### Malli → GraphQL (Lacinia)

| Malli Type | GraphQL Type | Serialization |
|------------|--------------|---------------|
| `:time/local-date` | `Date` (custom scalar) | ISO 8601 string (10 chars) |
| `:time/offset-date-time` | `DateTime` (custom scalar) | ISO 8601 string (~25 chars) |
| `:decimal` | `Decimal` (custom scalar) | String representation |
| `:enum` | Enum type | Uppercase enum values |

---

## Lacinia Custom Scalar Definitions

```clojure
(def custom-scalars
  {:Date 
   {:parse (fn [s] 
             (try 
               (LocalDate/parse s)
               (catch Exception e
                 (throw (ex-info "Invalid date format" {:value s})))))
    :serialize (fn [d] (str d))}
   
   :DateTime
   {:parse (fn [s]
             (try
               (OffsetDateTime/parse s)
               (catch Exception e
                 (throw (ex-info "Invalid datetime format" {:value s})))))
    :serialize (fn [dt] (str dt))}
   
   :Decimal
   {:parse (fn [s]
             (try
               (BigDecimal. s)
               (catch Exception e
                 (throw (ex-info "Invalid decimal format" {:value s})))))
    :serialize (fn [d] (str d))}})
```

---

## Registry Configuration

```clojure
(require '[malli.core :as m])
(require '[malli.registry :as mr])
(require '[malli.experimental.time :as met])

;; Complete registry with all custom types
(mr/set-default-registry!
  (mr/composite-registry
    (m/default-schemas)           ; Built-in Malli schemas
    (met/schemas)                 ; Experimental time schemas
    {:decimal (-decimal-schema)})) ; Custom decimal schema
```

---

## Key Insights

1. **Use Experimental Time Schemas**: Malli's `experimental.time` namespace provides production-ready temporal types that are well-tested and maintained.

2. **Custom Decimal Schema**: While Malli doesn't have built-in BigDecimal support, implementing a custom schema is straightforward using the `IntoSchema` protocol.

3. **Enum is Built-in**: No custom implementation needed for enums. Malli's `:enum` type works perfectly and can be easily mapped to GraphQL enum types.

4. **Transformation is Key**: All custom scalars need bidirectional transformation support:
   - **Parse**: String → Type (for GraphQL input)
   - **Serialize**: Type → String (for GraphQL output)

5. **Datomic Compatibility**: All recommended types map cleanly to Datomic's built-in types, ensuring data integrity.

---

## Ringline Framework Integration

### Files to Modify

1. **`src/ringline/schema/types.clj`**
   - Add new type mappings for Date, DateTime, Decimal, Enum
   - Extend `malli->datomic` and `malli->graphql` maps

2. **`src/ringline/schema/scalars.clj`** (NEW)
   - Implement custom `:decimal` schema
   - Define Lacinia custom scalar definitions
   - Provide transformation functions

3. **`src/ringline/schema/parser.clj`**
   - Handle new scalar types in field parsing
   - Extract enum values for GraphQL enum generation

4. **`src/ringline/schema/datomic.clj`**
   - Map new types to Datomic schema attributes

5. **`src/ringline/schema/lacinia.clj`**
   - Generate custom scalar definitions
   - Generate enum type definitions from Malli enums

6. **`src/ringline/mutation/transaction.clj`**
   - Add value conversions for new scalar types

7. **`src/ringline/response/transformer.clj`**
   - Add serialization for new scalar types

### Registry Setup

Add to `src/ringline/core.clj`:

```clojure
(require '[malli.experimental.time :as met])
(require '[ringline.schema.scalars :as scalars])

(defn- create-registry []
  (mr/composite-registry
    (m/default-schemas)
    (met/schemas)
    (scalars/custom-schemas)))
```

---

## Next Steps

1. Review `research-findings.md` for detailed explanations
2. Study `malli-type-examples.clj` for working code examples
3. Begin implementation following the checklist above
4. Test each scalar type independently before integration

---

## References

- **Malli GitHub**: https://github.com/metosin/malli
- **Malli Experimental Time**: `malli.experimental.time` namespace
- **Datomic Types**: https://docs.datomic.com/schema/schema-reference.html
- **Lacinia Custom Scalars**: https://lacinia.readthedocs.io/en/latest/custom-scalars.html
- **ISO 8601**: https://en.wikipedia.org/wiki/ISO_8601
- **Java Time API**: https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html

