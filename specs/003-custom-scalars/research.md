# Research: Custom Scalars Implementation

**Feature**: Custom Scalars Support  
**Branch**: `003-custom-scalars`  
**Date**: 2026-01-24

## Overview

This document consolidates research findings for implementing four custom scalar types (Date, DateTime, Enum, Decimal) in the Ringline framework. Each section provides decisions, rationale, and alternatives considered.

---

## 1. Malli Type Representation

### Decision: Use Malli Experimental Time + Custom Decimal Schema

**Chosen Approach:**

- **Date**: Use `:time/local-date` from `malli.experimental.time` (maps to `java.time.LocalDate`)
- **DateTime**: Use `:time/offset-date-time` from `malli.experimental.time` (maps to `java.time.OffsetDateTime`)
- **Enum**: Use built-in `:enum` type (e.g., `[:enum :draft :published :archived]`)
- **Decimal**: Implement custom `:decimal` schema using `IntoSchema` protocol

**Rationale:**

- Malli's experimental time schemas are production-ready and widely used (e.g., Metabase)
- `LocalDate` represents date-only values without time/timezone complexity
- `OffsetDateTime` preserves timezone offset (required by FR-009)
- Built-in `:enum` type maps perfectly to GraphQL enums and Datomic keywords
- Custom `:decimal` schema allows precision/scale validation properties

**Alternatives Considered:**

- **Use `:inst` for dates**: Rejected because `:inst` includes time component, requires midnight UTC convention
- **Use `:string` with regex validation**: Rejected because loses type safety and transformation capabilities
- **Use `:double` for decimals**: Rejected because floating-point errors violate precision requirements

**Implementation Notes:**

```clojure
;; Example schema using new types
(def Product
  [:map
   {:ringline/datomic-ns "product"}
   [:id :uuid]
   [:name :string]
   [:price [:decimal {:precision 38 :scale 10}]]
   [:status [:enum :draft :published :archived]]
   [:created-at :time/offset-date-time]
   [:launch-date {:optional true} :time/local-date]])
```

---

## 2. Lacinia Custom Scalar Definitions

### Decision: Define Custom Scalars with Parse/Serialize Functions

**Chosen Approach:**

- Define custom scalars in `:scalars` section of Lacinia schema
- Implement `:parse` function (GraphQL input → Java type)
- Implement `:serialize` function (Java type → GraphQL output)
- Use `attach-scalar-transformers` for EDN-based schemas

**Rationale:**

- Lacinia's custom scalar API is straightforward and well-documented
- Parse/serialize pattern matches GraphQL specification
- Throwing `ex-info` provides detailed error messages to clients
- `java.time` classes are thread-safe (unlike `SimpleDateFormat`)

**Example Implementation:**

```clojure
(require '[java.time LocalDate OffsetDateTime]
         '[java.time.format DateTimeFormatter DateTimeParseException])

(def custom-scalars
  {:Date
   {:parse (fn [value]
             (when (string? value)
               (try
                 (LocalDate/parse value DateTimeFormatter/ISO_LOCAL_DATE)
                 (catch DateTimeParseException e
                   (throw (ex-info
                           (str "Invalid date format. Expected YYYY-MM-DD, got: " value)
                           {:expected-format "YYYY-MM-DD" :received value}))))))
    :serialize (fn [value]
                 (cond
                   (instance? LocalDate value)
                   (.format value DateTimeFormatter/ISO_LOCAL_DATE)
                   (string? value) value
                   :else (throw (ex-info "Cannot serialize to Date" {:value value}))))}

   :DateTime
   {:parse (fn [value]
             (when (string? value)
               (try
                 (OffsetDateTime/parse value DateTimeFormatter/ISO_OFFSET_DATE_TIME)
                 (catch DateTimeParseException e
                   (throw (ex-info
                           (str "Invalid datetime format. Expected ISO8601 with timezone, got: " value)
                           {:expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM" :received value}))))))
    :serialize (fn [value]
                 (cond
                   (instance? OffsetDateTime value)
                   (.format value DateTimeFormatter/ISO_OFFSET_DATE_TIME)
                   (string? value) value
                   :else (throw (ex-info "Cannot serialize to DateTime" {:value value}))))}

   :Decimal
   {:parse (fn [value]
             (cond
               (instance? BigDecimal value) value
               (number? value) (bigdec value)
               (string? value) (try (BigDecimal. value)
                                    (catch NumberFormatException e
                                      (throw (ex-info "Invalid decimal format" {:received value}))))
               :else (throw (ex-info "Cannot parse as Decimal" {:received value}))))
    :serialize (fn [value]
                 (cond
                   (instance? BigDecimal value) (str value)
                   (number? value) (str (bigdec value))
                   :else (throw (ex-info "Cannot serialize to Decimal" {:value value}))))}})
```

**Alternatives Considered:**

- **Return `nil` on parse failure**: Rejected because generic error messages are less helpful
- **Use `SimpleDateFormat`**: Rejected because not thread-safe
- **Serialize decimals as numbers**: Rejected because JavaScript loses precision for large decimals

---

## 3. Datomic Type Choices

### Decision: Type Mappings with Timezone Preservation Strategy

**Chosen Mappings:**

| Scalar Type | Datomic Type                           | Java Type              | Notes                                |
| ----------- | -------------------------------------- | ---------------------- | ------------------------------------ |
| Date        | `:db.type/instant`                     | `java.util.Date`       | Store as midnight UTC                |
| DateTime    | `:db.type/instant` + `:db.type/string` | `java.util.Date`       | Store absolute time, return as UTC   |
| Enum        | `:db.type/keyword`                     | `clojure.lang.Keyword` | Simple, application-layer validation |
| Decimal     | `:db.type/bigdec`                      | `java.math.BigDecimal` | Enforce scale 10 in application      |

**Rationale:**

**Date (`:db.type/instant`):**

- Datomic has no date-only type
- Store as midnight UTC convention (standard practice)
- Convert `LocalDate` → `java.util.Date` at 00:00:00 UTC before storing
- Convert `java.util.Date` → `LocalDate` on retrieval (strip time component)

**DateTime :**

- **Critical**: `:db.type/instant` does NOT preserve timezone (stores UTC milliseconds only)
- Spec FR-009 requires timezone preservation
- On retrieval: Reconstruct `OffsetDateTime` from UTC instant

**Enum (`:db.type/keyword`):**

- Simpler than `:db.type/ref` with `:db/ident` entities
- Application-layer validation ensures only valid keywords stored
- Maps naturally to Malli `:enum` type
- GraphQL layer converts keywords to/from strings
- Alternative: `:db.type/ref` with `:db/ident` (more idiomatic Datomic, but adds complexity)

**Decimal (`:db.type/bigdec`):**

- Supported in Datomic Free 0.9.5697
- Precision limit: 1024 digits (spec requires 38, well within limit)
- Must enforce consistent scale (10 decimal places) in application layer
- Datomic documentation: "Consistent results in query depend on scale matching"

**Alternatives Considered:**

- **DateTime as ISO8601 string**: Rejected because loses Datomic temporal query capabilities
- **Enum as `:db.type/ref`**: Deferred for simplicity; can migrate later if needed
- **Decimal as integer cents**: Rejected because limited to currency use case

---

## 4. ISO8601 Parsing and Formatting

### Decision: Use java.time with DateTimeFormatter

**Chosen Approach:**

- **Date**: `java.time.LocalDate` with `DateTimeFormatter/ISO_LOCAL_DATE`
- **DateTime**: `java.time.OffsetDateTime` with `DateTimeFormatter/ISO_OFFSET_DATE_TIME`
- Validation: Parse with formatters (throws `DateTimeParseException` on invalid input)
- Regex pre-validation: Optional, parsing provides sufficient validation

**Rationale:**

- `java.time` is thread-safe (unlike `SimpleDateFormat`)
- Built-in ISO8601 formatters handle edge cases correctly
- `OffsetDateTime` preserves timezone offset (required by spec)
- Parsing exceptions provide detailed error messages

**Code Examples:**

```clojure
(import '[java.time LocalDate OffsetDateTime]
        '[java.time.format DateTimeFormatter DateTimeParseException])

;; Parse ISO8601 date (10 chars: "2024-01-15")
(defn parse-date [s]
  (try
    (LocalDate/parse s DateTimeFormatter/ISO_LOCAL_DATE)
    (catch DateTimeParseException e
      (throw (ex-info "Invalid date format" {:input s :expected "YYYY-MM-DD"})))))

;; Format LocalDate to ISO8601
(defn format-date [local-date]
  (.format local-date DateTimeFormatter/ISO_LOCAL_DATE))

;; Parse ISO8601 datetime with timezone (25 chars: "2024-01-15T14:30:00+05:00")
(defn parse-datetime [s]
  (try
    (OffsetDateTime/parse s DateTimeFormatter/ISO_OFFSET_DATE_TIME)
    (catch DateTimeParseException e
      (throw (ex-info "Invalid datetime format" {:input s :expected "YYYY-MM-DDTHH:MM:SS±HH:MM"})))))

;; Format OffsetDateTime to ISO8601
(defn format-datetime [offset-datetime]
  (.format offset-datetime DateTimeFormatter/ISO_OFFSET_DATE_TIME))

;; Validate datetime has timezone (reject "2024-01-15T14:30:00" without offset)
(defn validate-datetime-has-timezone [s]
  (when-not (re-find #"[+-]\d{2}:\d{2}|Z$" s)
    (throw (ex-info "DateTime must include timezone offset" {:input s}))))
```

**Performance:**

- `java.time` parsing: ~1-5 microseconds per parse (acceptable for GraphQL scalars)
- No significant performance concerns for typical GraphQL workloads

**Alternatives Considered:**

- **clojure.instant**: Rejected because limited to `java.util.Date`, no timezone preservation
- **Regex-only validation**: Rejected because doesn't validate semantic correctness (e.g., Feb 30)
- **Third-party libraries**: Rejected because `java.time` is standard and sufficient

---

## 5. Decimal Precision and Scale Validation

### Decision: Validate Precision/Scale in Application Layer

**Chosen Approach:**

- Use `java.math.BigDecimal` for all decimal values
- Extract precision and scale using `.precision()` and `.scale()` methods
- Validate: precision ≤ 38, scale ≤ 10 (per spec requirements)
- Serialize as string in GraphQL to avoid JavaScript precision loss

**Code Examples:**

```clojure
(defn validate-decimal-precision-scale
  "Validate BigDecimal precision and scale limits.
   Precision: total number of digits (max 38)
   Scale: number of digits after decimal point (max 10)"
  [^BigDecimal bd]
  (let [precision (.precision bd)
        scale (.scale bd)]
    (when (> precision 38)
      (throw (ex-info "Decimal precision exceeds limit"
                      {:value (str bd) :precision precision :max-precision 38})))
    (when (> scale 10)
      (throw (ex-info "Decimal scale exceeds limit"
                      {:value (str bd) :scale scale :max-scale 10})))
    bd))

(defn parse-decimal
  "Parse string or number to BigDecimal with validation"
  [value]
  (let [bd (cond
             (instance? BigDecimal value) value
             (number? value) (bigdec value)
             (string? value) (BigDecimal. value)
             :else (throw (ex-info "Invalid decimal value" {:value value})))]
    (validate-decimal-precision-scale bd)))

(defn serialize-decimal
  "Serialize BigDecimal to string (avoid JavaScript precision loss)"
  [^BigDecimal bd]
  (str bd))
```

**Rationale:**

- BigDecimal precision: Total significant digits (e.g., "123.45" has precision 5)
- BigDecimal scale: Digits after decimal point (e.g., "123.45" has scale 2)
- Spec requires: precision ≤ 38, scale ≤ 10
- String serialization prevents JavaScript `Number` precision loss (max safe integer: 2^53-1)

**Performance:**

- BigDecimal arithmetic: ~10-100x slower than primitive doubles
- Acceptable for business logic (currency, measurements)
- Not suitable for high-frequency numeric computation

**Alternatives Considered:**

- **Store as integer cents**: Rejected because limited to 2 decimal places
- **Use Double**: Rejected because floating-point errors violate spec
- **Serialize as number**: Rejected because JavaScript loses precision

---

## 6. Enum Validation with Case Mismatch Suggestions

### Decision: Case-Sensitive Matching with Case-Insensitive Suggestions

**Chosen Approach:**

- Validate enum values case-sensitively (per spec FR-014)
- On mismatch: Check for case-insensitive match to suggest correct casing
- Error message format: "Invalid value 'active'. Valid options: ACTIVE, INACTIVE. Did you mean 'ACTIVE'?"

**Code Examples:**

```clojure
(defn find-case-mismatch
  "Find case-insensitive match in valid options"
  [input valid-options]
  (let [input-lower (clojure.string/lower-case (name input))]
    (first (filter #(= input-lower (clojure.string/lower-case (name %)))
                   valid-options))))

(defn validate-enum
  "Validate enum value with case mismatch suggestions"
  [value valid-options]
  (let [value-kw (keyword value)]
    (if (contains? (set valid-options) value-kw)
      value-kw
      (let [suggestion (find-case-mismatch value valid-options)
            valid-str (clojure.string/join ", " (map name valid-options))
            msg (if suggestion
                  (format "Invalid value '%s'. Valid options: %s. Did you mean '%s'?"
                          value valid-str (name suggestion))
                  (format "Invalid value '%s'. Valid options: %s"
                          value valid-str))]
        (throw (ex-info msg {:value value :valid-options valid-options :suggestion suggestion}))))))

;; Example usage
(validate-enum "active" [:ACTIVE :INACTIVE :PENDING])
;; => Throws: "Invalid value 'active'. Valid options: ACTIVE, INACTIVE, PENDING. Did you mean 'ACTIVE'?"
```

**Rationale:**

- Case-sensitive matching is GraphQL convention
- Case mismatch suggestions improve developer experience (per spec FR-016)
- Simple case-insensitive comparison is sufficient (no need for Levenshtein distance)
- Performance: O(n) where n = number of enum options (typically small)

**Alternatives Considered:**

- **Levenshtein distance**: Rejected as over-engineering for case mismatch detection
- **Case-insensitive matching**: Rejected because violates spec FR-014
- **No suggestions**: Rejected because spec FR-016 requires suggestions

---

## 7. Performance Benchmarks and Goals

### Decision: Target <1ms per Scalar Conversion

**Performance Goals:**

- Date parsing: <100 microseconds
- DateTime parsing: <100 microseconds
- Decimal validation: <50 microseconds
- Enum validation: <10 microseconds

**Rationale:**

- GraphQL queries typically involve 10-100 scalar conversions
- Target total scalar overhead: <10ms per query
- Acceptable for typical web API latency budgets (50-200ms)

**Benchmarking Strategy:**

- Use `criterium` library for micro-benchmarks
- Test with realistic data (valid and invalid inputs)
- Profile integration tests to measure end-to-end impact

**Optimization Opportunities:**

- Cache compiled regex patterns
- Reuse DateTimeFormatter instances (thread-safe)
- Memoize enum validation for repeated values

**Alternatives Considered:**

- **Pre-compile all scalars**: Rejected because dynamic schema generation is core feature
- **Skip validation**: Rejected because violates spec requirements

---

## Summary of Decisions

| Aspect             | Decision                                                                 | Key Rationale                             |
| ------------------ | ------------------------------------------------------------------------ | ----------------------------------------- |
| Malli Types        | `:time/local-date`, `:time/offset-date-time`, `:enum`, custom `:decimal` | Production-ready, type-safe, composable   |
| Lacinia Scalars    | Custom scalars with parse/serialize functions                            | Standard GraphQL pattern, detailed errors |
| Datomic Date       | `:db.type/instant` (midnight UTC)                                        | No date-only type available               |
| Datomic DateTime   | `:db.type/instant` + `:db.type/string` (timezone)                        | Preserves timezone per spec FR-009        |
| Datomic Enum       | `:db.type/keyword`                                                       | Simple, application-layer validation      |
| Datomic Decimal    | `:db.type/bigdec`                                                        | Arbitrary precision, scale enforcement    |
| ISO8601 Parsing    | `java.time` with `DateTimeFormatter`                                     | Thread-safe, standard, robust             |
| Decimal Validation | `.precision()` ≤ 38, `.scale()` ≤ 10                                     | Per spec requirements                     |
| Enum Suggestions   | Case-insensitive match for suggestions                                   | Improves DX per spec FR-016               |
| Performance Target | <1ms per scalar conversion                                               | Acceptable for web API latency            |

---

## Open Questions / Risks

1. **Enum Storage**: `:db.type/keyword` vs `:db.type/ref` tradeoff. Current choice favors simplicity.
2. **Decimal Scale Consistency**: Must enforce scale 10 consistently. Consider adding Malli property to specify scale per field.
3. **Performance**: Benchmarking needed to validate <1ms target under realistic load.

---

**Next Steps**: Proceed to Phase 1 (Data Model & Contracts)
