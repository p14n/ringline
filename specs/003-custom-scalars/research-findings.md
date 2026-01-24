# Research Findings: Date, DateTime, Decimal, and Enum Types in Malli

**Date**: 2026-01-24  
**Feature**: 003-custom-scalars  
**Research Focus**: How to represent Date, DateTime, Decimal, and Enum types in Malli schemas for Ringline framework

---

## Executive Summary

Malli does **not** have built-in support for date-only types or BigDecimal types in its core. However, it provides:
1. **`:inst`** - Built-in instant/timestamp type (maps to `java.util.Date` / `js/Date`)
2. **`malli.experimental.time`** - Experimental namespace with comprehensive temporal types including `LocalDate` and `OffsetDateTime`
3. **`:enum`** - Built-in enum type that works well for constrained values
4. **Custom schema extension** - Well-documented protocol-based system for adding new types

**Recommendation**: Use `malli.experimental.time` schemas for temporal types and create custom `:decimal` schema for BigDecimal support.

---

## Research Question 1: Date-Only Types (Without Time)

### Malli's Built-in Support

**Answer**: No built-in date-only type in core, but **YES in `malli.experimental.time`**

The `malli.experimental.time` namespace provides:
- **`:time/local-date`** - Date without time (e.g., `2020-01-01`)
  - Maps to `java.time.LocalDate` (JVM) or `js-joda LocalDate` (ClojureScript)
  - ISO 8601 format: `YYYY-MM-DD` (10 characters)
  - Supports `:min` and `:max` properties for validation

### Example Usage

```clojure
(require '[malli.experimental.time :as met])
(require '[malli.core :as m])
(require '[malli.registry :as mr])

;; Add time schemas to registry
(mr/set-default-registry!
  (mr/composite-registry
    (m/default-schemas)
    (met/schemas)))

;; Define schema with date field
[:map
 [:birth-date :time/local-date]
 [:hire-date [:time/local-date {:min (LocalDate/parse "2020-01-01")}]]]
```

### Transformation Support

`malli.experimental.time.transform` provides:
- **String → LocalDate** parsing with configurable patterns
- **LocalDate → String** serialization with configurable formats
- Default ISO 8601 format (`yyyy-MM-dd`)

```clojure
(require '[malli.experimental.time.transform :as mett])

;; Custom date format
(m/decode 
  [:time/local-date {:pattern "yyyyMMdd"}] 
  "20200101" 
  (mett/time-transformer))
;; => #object[java.time.LocalDate "2020-01-01"]
```

---

## Research Question 2: DateTime with Timezone

### Malli's Built-in Support

**Answer**: YES in `malli.experimental.time`

The experimental time namespace provides **three** datetime types:

1. **`:time/offset-date-time`** - DateTime with timezone offset (RECOMMENDED)
   - Maps to `java.time.OffsetDateTime`
   - ISO 8601 format: `2022-12-18T06:00:25.840823567-06:00` (~25-30 chars)
   - Preserves timezone offset information

2. **`:time/zoned-date-time`** - DateTime with full timezone ID
   - Maps to `java.time.ZonedDateTime`
   - ISO 8601 format: `2022-12-18T06:00:25.840823567-06:00[America/Chicago]`
   - Preserves full timezone context

3. **`:time/local-date-time`** - DateTime without timezone
   - Maps to `java.time.LocalDateTime`
   - ISO 8601 format: `2020-01-01T12:00:00`
   - No timezone information

### Recommendation for Ringline

Use **`:time/offset-date-time`** for datetime fields:
- Preserves timezone information (critical for distributed systems)
- ISO 8601 compliant
- Serializes to ~25 character strings (matches spec requirement)
- Supported by both JVM and ClojureScript (via js-joda)

---

## Research Question 3: BigDecimal / Precise Numeric Types

### Malli's Built-in Support

**Answer**: NO built-in BigDecimal type

Malli core provides:
- **`:int`** - Integer/long values
- **`:double`** - Floating point (NOT suitable for precise decimals)
- **Predicate schemas** - Can use `decimal?` predicate, but no schema properties

### Custom Schema Approach (RECOMMENDED)

Create a custom `:decimal` schema type following Malli's extension pattern:

```clojure
(ns ringline.schema.scalars
  (:require [malli.core :as m]))

(defn -decimal-schema []
  ^{:type ::m/into-schema}
  (reify m/IntoSchema
    (-type [_] :decimal)
    (-type-properties [_] nil)
    (-properties-schema [_ _] nil)
    (-children-schema [_ _] nil)
    (-into-schema [parent properties children options]
      ^{:type ::m/schema}
      (reify m/Schema
        (-validator [_]
          (fn [x] (instance? BigDecimal x)))
        (-explainer [this path]
          (fn explain [x in acc]
            (if-not (instance? BigDecimal x)
              (conj acc (m/-error path in this x ::invalid-decimal))
              acc)))
        (-parser [_]
          (fn [x]
            (if (instance? BigDecimal x)
              x
              ::m/invalid)))
        (-unparser [_]
          (fn [x]
            (if (instance? BigDecimal x)
              x
              ::m/invalid)))
        (-transformer [this transformer method options]
          (m/-value-transformer transformer this method options))
        (-walk [this walker path options]
          (m/-walk-leaf this walker path options))
        (-properties [_] properties)
        (-options [_] options)
        (-children [_] children)
        (-parent [_] parent)
        (-form [_] :decimal)))))

;; Register custom schema
(def custom-schemas
  {:decimal (-decimal-schema)})
```

### Datomic Mapping

Datomic provides **`:db.type/bigdec`** for arbitrary-precision decimals:
- Stores exact decimal values
- No precision loss
- Suitable for financial calculations

---

## Research Question 4: Extending Malli's Type System

### Best Practices for Custom Types

Based on Malli documentation and community examples:

1. **Implement `IntoSchema` Protocol**
   - Define `-type`, `-validator`, `-explainer`, `-parser`, `-transformer`
   - Return reified `Schema` instance

2. **Register in Custom Registry**
   ```clojure
   (mr/composite-registry
     (m/default-schemas)
     (met/schemas)           ; Time schemas
     {:decimal (-decimal-schema)})  ; Custom schemas
   ```

3. **Add Transformation Support**
   - Implement string → type parsing
   - Implement type → string serialization
   - Handle validation errors gracefully

4. **Provide Generator Support** (optional)
   - Implement `malli.generator/-generator` for test data generation

### Simpler Alternative: Schema Composition

For simpler cases, use Malli's built-in composition:

```clojure
;; Decimal as refined double with validation
(def Decimal
  [:and
   [:fn {:error/message "must be a BigDecimal"}
    #(instance? BigDecimal %)]
   [:fn {:error/message "must have at most 2 decimal places"}
    #(-> % (.scale) (<= 2))]])
```

---

## Research Question 5: Enum Type Usage

### Malli's Built-in `:enum` Type

**Answer**: YES, use Malli's `:enum` type

Malli provides excellent enum support:

```clojure
[:enum :pending :in-progress :completed :cancelled]
```

**Features**:
- Built-in validation
- Clear error messages
- Works with any Clojure values (keywords, strings, numbers)
- Supports schema properties

### GraphQL Enum Mapping

For GraphQL, enums should map to Lacinia enum definitions:

```clojure
;; Malli schema
[:status [:enum :pending :in-progress :completed]]

;; Lacinia enum (generated)
{:enums
 {:Status {:values [:PENDING :IN_PROGRESS :COMPLETED]}}}
```

**Recommendation**: 
- Use **`:enum`** in Malli schemas
- Generate Lacinia enum types from Malli enum values
- Map enum keywords to uppercase GraphQL enum values (convention)

---

## Implementation Recommendations

### 1. Temporal Types

**Use `malli.experimental.time` schemas**:
- `:time/local-date` for date-only fields
- `:time/offset-date-time` for datetime with timezone
- Add `(met/schemas)` to Ringline's default registry

### 2. Decimal Type

**Create custom `:decimal` schema**:
- Implement `IntoSchema` protocol
- Add string ↔ BigDecimal transformers
- Map to `:db.type/bigdec` (Datomic)
- Map to custom `Decimal` scalar (GraphQL)

### 3. Enum Type

**Use built-in `:enum`**:
- No custom implementation needed
- Generate GraphQL enum types from Malli enum values
- Map to `:db.type/keyword` (Datomic)

### 4. Type Mappings Extension

Update `ringline.schema.types`:

```clojure
(def malli->datomic
  {:string  :db.type/string
   :int     :db.type/long
   :double  :db.type/double
   :boolean :db.type/boolean
   :uuid    :db.type/uuid
   :inst    :db.type/instant
   :keyword :db.type/keyword
   :ref     :db.type/ref
   ;; NEW TYPES
   :time/local-date      :db.type/instant  ; Store as instant, validate format
   :time/offset-date-time :db.type/instant
   :decimal :db.type/bigdec
   :enum    :db.type/keyword})

(def malli->graphql
  {:string  'String
   :int     'Int
   :double  'Float
   :boolean 'Boolean
   :uuid    'ID
   :inst    'String
   :keyword 'String
   ;; NEW TYPES
   :time/local-date       'Date      ; Custom scalar
   :time/offset-date-time 'DateTime  ; Custom scalar
   :decimal 'Decimal                 ; Custom scalar
   :enum    'Enum})                  ; Will be refined to specific enum type
```

---

## Code Examples

### Complete Schema Example

```clojure
(def Product
  [:map
   {:ringline/datomic-ns "product"}
   [:id :uuid]
   [:name :string]
   [:price :decimal]
   [:status [:enum :draft :published :archived]]
   [:created-at :time/offset-date-time]
   [:launch-date {:optional true} :time/local-date]])
```

### Lacinia Custom Scalar Definition

```clojure
(def custom-scalars
  {:Date {:parse (fn [s] (LocalDate/parse s))
          :serialize (fn [d] (str d))}
   :DateTime {:parse (fn [s] (OffsetDateTime/parse s))
              :serialize (fn [dt] (str dt))}
   :Decimal {:parse (fn [s] (BigDecimal. s))
             :serialize (fn [d] (str d))}})
```

---

## References

1. **Malli GitHub**: https://github.com/metosin/malli
2. **Malli Experimental Time**: `malli.experimental.time` namespace
3. **Datomic Schema Reference**: https://docs.datomic.com/schema/schema-reference.html
4. **Lacinia Documentation**: https://lacinia.readthedocs.io/
5. **ISO 8601 Standard**: Date and time format specifications

