# Data Model: Custom Scalars

**Feature**: Custom Scalars Support  
**Branch**: `003-custom-scalars`  
**Date**: 2026-01-24

## Overview

This document defines the data model for four custom scalar types in the Ringline framework. Each scalar type includes Malli schema representation, validation rules, and conversion logic.

---

## 1. Date Scalar

### Malli Schema Representation

```clojure
;; Use Malli experimental time type
:time/local-date

;; Example in entity schema
(def Event
  [:map
   {:ringline/datomic-ns "event"}
   [:id :uuid]
   [:name :string]
   [:event-date :time/local-date]  ; Calendar date without time
   [:deadline {:optional true} :time/local-date]])
```

### Validation Rules

- **Format**: ISO8601 date string (10 characters: `YYYY-MM-DD`)
- **Valid examples**: `"2024-01-15"`, `"2000-12-31"`, `"1999-01-01"`
- **Invalid examples**: `"01/15/2024"`, `"2024-1-15"`, `"2024-01-32"`, `"2024-02-30"`
- **Year range**: No restrictions (accept any valid ISO8601 date per spec clarification)
- **Semantic validation**: Must be valid calendar date (e.g., reject Feb 30)

### Type Conversions

**GraphQL → Malli (Parse):**

```clojure
(import '[java.time LocalDate]
        '[java.time.format DateTimeFormatter DateTimeParseException])

(defn parse-date
  "Parse ISO8601 date string to LocalDate"
  [s]
  (when (string? s)
    (try
      (LocalDate/parse s DateTimeFormatter/ISO_LOCAL_DATE)
      (catch DateTimeParseException e
        (throw (ex-info
                (str "Invalid date format. Expected YYYY-MM-DD, got: " s)
                {:expected-format "YYYY-MM-DD" :received s}))))))
```

**Malli → Datomic (Store):**

```clojure
(import '[java.util Date]
        '[java.time ZoneOffset])

(defn local-date->instant
  "Convert LocalDate to java.util.Date at midnight UTC"
  [^LocalDate ld]
  (-> ld
      (.atStartOfDay)
      (.toInstant ZoneOffset/UTC)
      (Date/from)))
```

**Datomic → GraphQL (Serialize):**

```clojure
(defn instant->local-date
  "Convert java.util.Date to LocalDate (strip time component)"
  [^Date date]
  (-> date
      (.toInstant)
      (.atZone ZoneOffset/UTC)
      (.toLocalDate)))

(defn serialize-date
  "Serialize LocalDate to ISO8601 string"
  [^LocalDate ld]
  (.format ld DateTimeFormatter/ISO_LOCAL_DATE))
```

### Datomic Schema

```clojure
{:db/ident :event/event-date
 :db/valueType :db.type/instant
 :db/cardinality :db.cardinality/one
 :db/doc "Event date (stored as midnight UTC)"}
```

---

## 2. DateTime Scalar

### Malli Schema Representation

```clojure
;; Use Malli experimental time type
:time/offset-date-time

;; Example in entity schema
(def Task
  [:map
   {:ringline/datomic-ns "task"}
   [:id :uuid]
   [:title :string]
   [:created-at :time/offset-date-time]  ; Timestamp with timezone
   [:updated-at :time/offset-date-time]
   [:scheduled-for {:optional true} :time/offset-date-time]])
```

### Validation Rules

- **Format**: ISO8601 datetime string with timezone (approximately 25 characters: `YYYY-MM-DDTHH:MM:SS±HH:MM`)
- **Valid examples**: `"2024-01-15T14:30:00+05:00"`, `"2024-01-15T14:30:00Z"`, `"2024-01-15T14:30:00-08:00"`
- **Invalid examples**: `"2024-01-15T14:30:00"` (no timezone), `"2024-01-15 14:30:00"`, `"2024-01-15T25:00:00+00:00"`
- **Timezone requirement**: MUST include timezone offset (per spec FR-010)
- **Timezone validation**: Offset must be valid (e.g., reject `+99:99`)

### Type Conversions

**GraphQL → Malli (Parse):**

```clojure
(import '[java.time OffsetDateTime]
        '[java.time.format DateTimeFormatter DateTimeParseException])

(defn validate-has-timezone
  "Validate datetime string includes timezone offset"
  [s]
  (when-not (re-find #"[+-]\d{2}:\d{2}|Z$" s)
    (throw (ex-info
            "DateTime must include timezone offset"
            {:received s :expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM"}))))

(defn parse-datetime
  "Parse ISO8601 datetime string with timezone to OffsetDateTime"
  [s]
  (when (string? s)
    (validate-has-timezone s)
    (try
      (OffsetDateTime/parse s DateTimeFormatter/ISO_OFFSET_DATE_TIME)
      (catch DateTimeParseException e
        (throw (ex-info
                (str "Invalid datetime format. Expected ISO8601 with timezone, got: " s)
                {:expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM" :received s}))))))
```

**Datomic → GraphQL (Serialize):**

```clojure
(import '[java.time Instant ZoneOffset OffsetDateTime])

(defn instant-and-offset->offset-datetime
  "Reconstruct OffsetDateTime from java.util.Date and timezone offset string"
  [^Date date offset-str]
  (let [instant (.toInstant date)
        offset (ZoneOffset/of offset-str)]
    (OffsetDateTime/ofInstant instant offset)))

(defn serialize-datetime
  "Serialize OffsetDateTime to ISO8601 string"
  [^OffsetDateTime odt]
  (.format odt DateTimeFormatter/ISO_OFFSET_DATE_TIME))
```

### Datomic Schema

```clojure
;; Primary attribute: UTC timestamp
{:db/ident :task/scheduled-for
 :db/valueType :db.type/instant
 :db/cardinality :db.cardinality/one
 :db/doc "Scheduled datetime (UTC)"}

;; Secondary attribute: Timezone offset
{:db/ident :task/scheduled-for-tz
 :db/valueType :db.type/string
 :db/cardinality :db.cardinality/one
 :db/doc "Timezone offset for scheduled-for (e.g., '+05:00')"}
```

---

## 3. Enum Scalar

### Malli Schema Representation

```clojure
;; Use built-in :enum type
[:enum :option1 :option2 :option3]

;; Example in entity schema
(def Task
  [:map
   {:ringline/datomic-ns "task"}
   [:id :uuid]
   [:title :string]
   [:status [:enum :draft :in-progress :completed :cancelled]]
   [:priority [:enum :low :medium :high :urgent]]])
```

### Validation Rules

- **Case sensitivity**: Case-sensitive matching (per spec FR-014)
- **Valid examples**: `:draft`, `:in-progress`, `:completed`
- **Invalid examples**: `:Draft`, `:DRAFT`, `:in_progress`, `:unknown`
- **Error messages**: Must list valid options and suggest case-corrected alternative if case mismatch detected (per spec FR-016)

### Type Conversions

**GraphQL → Malli (Parse):**

```clojure
(defn find-case-mismatch
  "Find case-insensitive match in valid options"
  [input valid-options]
  (let [input-lower (clojure.string/lower-case (name input))]
    (first (filter #(= input-lower (clojure.string/lower-case (name %)))
                   valid-options))))

(defn parse-enum
  "Parse and validate enum value with case mismatch suggestions"
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
        (throw (ex-info msg {:value value
                             :valid-options valid-options
                             :suggestion suggestion}))))))
```

**Malli → Datomic (Store):**

```clojure
;; Keywords pass through directly
(defn enum->keyword [enum-value] enum-value)
```

**Datomic → GraphQL (Serialize):**

```clojure
(defn serialize-enum
  "Serialize keyword to string for GraphQL"
  [kw]
  (name kw))
```

### Datomic Schema

```clojure
{:db/ident :task/status
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/one
 :db/doc "Task status (enum)"}
```

---

## 4. Decimal Scalar

### Malli Schema Representation

```clojure
;; Custom :decimal type with precision/scale properties
[:decimal {:precision 38 :scale 10}]

;; Example in entity schema
(def Product
  [:map
   {:ringline/datomic-ns "product"}
   [:id :uuid]
   [:name :string]
   [:price [:decimal {:precision 38 :scale 10}]]
   [:weight-kg {:optional true} [:decimal {:precision 38 :scale 10}]]])
```

### Validation Rules

- **Precision limit**: Maximum 38 total digits (per spec clarification)
- **Scale limit**: Maximum 10 digits after decimal point (per spec clarification)
- **Valid examples**: `19.99`, `1234567890.1234567890`, `0.0000000001`
- **Invalid examples**: `12345678901234567890123456789012345678.9` (39 digits), `1.12345678901` (11 decimal places)
- **Error messages**: Must specify precision/scale limits when exceeded (per spec FR-021)

### Type Conversions

**GraphQL → Malli (Parse):**

```clojure
(defn validate-decimal-precision-scale
  "Validate BigDecimal precision and scale limits"
  [^BigDecimal bd max-precision max-scale]
  (let [precision (.precision bd)
        scale (.scale bd)]
    (when (> precision max-precision)
      (throw (ex-info
              (format "Decimal precision exceeds limit of %d (got %d)" max-precision precision)
              {:value (str bd) :precision precision :max-precision max-precision})))
    (when (> scale max-scale)
      (throw (ex-info
              (format "Decimal scale exceeds limit of %d (got %d)" max-scale scale)
              {:value (str bd) :scale scale :max-scale max-scale})))
    bd))

(defn parse-decimal
  "Parse string or number to BigDecimal with validation"
  [value {:keys [precision scale] :or {precision 38 scale 10}}]
  (let [bd (cond
             (instance? BigDecimal value) value
             (number? value) (bigdec value)
             (string? value) (try (BigDecimal. value)
                                  (catch NumberFormatException e
                                    (throw (ex-info "Invalid decimal format" {:received value}))))
             :else (throw (ex-info "Cannot parse as Decimal" {:received value})))]
    (validate-decimal-precision-scale bd precision scale)))
```

**Malli → Datomic (Store):**

```clojure
;; BigDecimal passes through directly (Datomic supports :db.type/bigdec)
(defn decimal->bigdec [bd] bd)
```

**Datomic → GraphQL (Serialize):**

```clojure
(defn serialize-decimal
  "Serialize BigDecimal to string (avoid JavaScript precision loss)"
  [^BigDecimal bd]
  (str bd))
```

### Datomic Schema

```clojure
{:db/ident :product/price
 :db/valueType :db.type/bigdec
 :db/cardinality :db.cardinality/one
 :db/doc "Product price (precise decimal)"}
```

---

## Summary Table

| Scalar   | Malli Type               | Datomic Type(s)                        | GraphQL Type        | Parse Input               | Serialize Output       |
| -------- | ------------------------ | -------------------------------------- | ------------------- | ------------------------- | ---------------------- |
| Date     | `:time/local-date`       | `:db.type/instant`                     | `Date` (custom)     | ISO8601 string (10 chars) | ISO8601 string         |
| DateTime | `:time/offset-date-time` | `:db.type/instant` + `:db.type/string` | `DateTime` (custom) | ISO8601 string with TZ    | ISO8601 string with TZ |
| Enum     | `[:enum ...]`            | `:db.type/keyword`                     | Enum type           | String                    | String                 |
| Decimal  | `[:decimal {...}]`       | `:db.type/bigdec`                      | `Decimal` (custom)  | String or number          | String                 |

---

## State Transitions

Not applicable - scalar types do not have state transitions.

---

## Relationships

Not applicable - scalar types are primitive values, not entities with relationships.

---

**Next**: See `contracts/` directory for GraphQL schema definitions and example queries.
