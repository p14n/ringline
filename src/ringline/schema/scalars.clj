(ns ringline.schema.scalars
  "Custom scalar type definitions and validation logic for Ringline framework.

  Provides support for four custom scalar types:
  - Date: Calendar dates without time information (ISO8601: YYYY-MM-DD)
  - DateTime: Timestamps with timezone (ISO8601: YYYY-MM-DDTHH:MM:SS±HH:MM)
  - Enum: Constrained values from predefined set
  - Decimal: Precise numeric values without floating-point errors

  This namespace handles:
  - Malli schema registration for time types
  - Custom :decimal schema implementation
  - Parse functions (GraphQL → Java types)
  - Serialize functions (Java types → GraphQL)
  - Store functions (Java types → Datomic)
  - Validation logic for all scalar types"
  (:require [malli.core :as m]
            [malli.registry :as mr]
            [malli.experimental.time :as met])
  (:import [java.time LocalDate OffsetDateTime Instant ZoneOffset]
           [java.time.format DateTimeFormatter DateTimeParseException]
           [java.math BigDecimal]))

;; ============================================================================
;; T019: Malli Registry Setup - Time Types
;; ============================================================================

;; Time schemas are registered from malli.experimental.time
;; Available types:
;; - :time/local-date (java.time.LocalDate)
;; - :time/offset-date-time (java.time.OffsetDateTime)
;; These are registered via (met/schemas) in the composite registry

;; ============================================================================
;; T020: Custom :decimal Schema Implementation
;; ============================================================================

(defn -decimal-schema
  "Custom Malli schema for BigDecimal values with precision/scale validation.

  Properties:
  - :precision - Maximum total digits (default: 38)
  - :scale - Maximum digits after decimal point (default: 10)

  Example: [:decimal {:precision 38 :scale 10}]"
  []
  ^{:type ::m/into-schema}
  (reify m/IntoSchema
    (-type [_] :decimal)
    (-type-properties [_] nil)
    (-properties-schema [_ _] nil)
    (-children-schema [_ _] nil)
    (-into-schema [parent properties children options]
      (let [{:keys [precision scale] :or {precision 38 scale 10}} properties]
        ^{:type ::m/schema}
        (reify m/Schema
          (-validator [_]
            (fn [x]
              (and (instance? BigDecimal x)
                   (<= (.precision ^BigDecimal x) precision)
                   (<= (.scale ^BigDecimal x) scale))))
          (-explainer [this path]
            (fn explain [x in acc]
              (cond
                (not (instance? BigDecimal x))
                (conj acc {:path path
                           :in in
                           :schema this
                           :value x
                           :type ::not-decimal
                           :message "Value is not a BigDecimal"})

                (> (.precision ^BigDecimal x) precision)
                (conj acc {:path path
                           :in in
                           :schema this
                           :value x
                           :type ::precision-exceeded
                           :precision (.precision ^BigDecimal x)
                           :max-precision precision
                           :message (format "Decimal precision exceeds limit of %d (got %d)"
                                          precision (.precision ^BigDecimal x))})

                (> (.scale ^BigDecimal x) scale)
                (conj acc {:path path
                           :in in
                           :schema this
                           :value x
                           :type ::scale-exceeded
                           :scale (.scale ^BigDecimal x)
                           :max-scale scale
                           :message (format "Decimal scale exceeds limit of %d (got %d)"
                                          scale (.scale ^BigDecimal x))})

                :else acc)))
          (-parser [_]
            (fn [x]
              (if (instance? BigDecimal x) x ::m/invalid)))
          (-unparser [_]
            (fn [x]
              (if (instance? BigDecimal x) x ::m/invalid)))
          (-transformer [this transformer method options]
            (m/-value-transformer transformer this method options))
          (-walk [this walker path options]
            (m/-walk-leaf this walker path options))
          (-properties [_] properties)
          (-options [_] options)
          (-children [_] children)
          (-parent [_] parent)
          (-form [_] (if (seq properties)
                       [:decimal properties]
                       :decimal)))))))

(defn custom-schemas
  "Registry of custom Malli schemas for Ringline.

  Returns map of custom schema definitions to be merged into composite registry."
  []
  {:decimal (-decimal-schema)})

;; ============================================================================
;; T027-T029: Date Scalar Functions
;; ============================================================================

(defn parse-date
  "Parse ISO8601 date string to LocalDate.

  Accepts format: YYYY-MM-DD (10 characters)

  Args:
    s - ISO8601 date string

  Returns:
    java.time.LocalDate instance

  Throws:
    ex-info if format is invalid or date values are invalid"
  [s]
  (try
    (LocalDate/parse s DateTimeFormatter/ISO_LOCAL_DATE)
    (catch DateTimeParseException e
      (throw (ex-info "Invalid date format"
                      {:value s
                       :expected "ISO8601 format: YYYY-MM-DD"
                       :example "2024-01-24"}
                      e)))
    (catch Exception e
      (throw (ex-info "Invalid date"
                      {:value s}
                      e)))))

(defn serialize-date
  "Serialize LocalDate or java.util.Date to ISO8601 string.

  Returns format: YYYY-MM-DD (10 characters)

  Args:
    date - java.time.LocalDate or java.util.Date instance

  Returns:
    ISO8601 formatted string"
  [date]
  (cond
    (instance? LocalDate date)
    (.format ^LocalDate date DateTimeFormatter/ISO_LOCAL_DATE)

    (instance? java.util.Date date)
    ;; Convert java.util.Date to LocalDate (assumes UTC)
    (let [instant (.toInstant ^java.util.Date date)
          local-date (.atZone instant ZoneOffset/UTC)]
      (.format local-date DateTimeFormatter/ISO_LOCAL_DATE))

    :else
    (throw (ex-info "Invalid date type for serialization"
                    {:value date :type (type date)}))))

(defn store-date
  "Convert LocalDate to java.util.Date for Datomic storage.

  Stores date as midnight UTC to preserve date-only semantics.
  Datomic :db.type/instant requires java.util.Date, not java.time.Instant.

  Args:
    date - java.time.LocalDate instance

  Returns:
    java.util.Date at midnight UTC"
  [^LocalDate date]
  (java.util.Date/from (.toInstant (.atStartOfDay date ZoneOffset/UTC))))



;; ============================================================================
;; DateTime Scalar Functions
;; ============================================================================

;; T049: Validate datetime has timezone (implemented first as it's used by parse-datetime)
(defn validate-datetime-has-timezone
  "Validate that datetime string includes timezone information.

  Returns nil if timezone present, throws ex-info if missing.

  Accepts:
  - Positive offset: +HH:MM (e.g., \"+05:00\")
  - Negative offset: -HH:MM (e.g., \"-08:00\")
  - UTC: Z

  Rejects datetime strings without timezone per FR-010."
  [s]
  (when-not (re-find #"[+-]\d{2}:\d{2}|Z$" s)
    (throw (ex-info
            "DateTime must include timezone offset"
            {:received s
             :expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM"
             :error-type :missing-timezone}))))

;; T046: Parse datetime function
(defn parse-datetime
  "Parse ISO8601 datetime string with timezone to OffsetDateTime.

  Format: YYYY-MM-DDTHH:MM:SS±HH:MM (approximately 25 characters)
  Example: \"2024-01-15T14:30:00+05:00\"

  MUST include timezone offset. Rejects datetime without timezone.

  Returns OffsetDateTime on success, throws ex-info on failure."
  [s]
  (when (string? s)
    ;; Try to parse first - this will catch malformed datetime strings
    (try
      (let [result (OffsetDateTime/parse s DateTimeFormatter/ISO_OFFSET_DATE_TIME)]
        ;; After successful parse, validate timezone is present
        ;; (This catches edge cases where parser might accept without timezone)
        (validate-datetime-has-timezone s)
        result)
      (catch DateTimeParseException e
        ;; Check if the string looks like a datetime (contains 'T' separator)
        ;; If it does, check if it's missing timezone
        (if (re-find #"T" s)
          ;; Looks like a datetime - check if timezone is missing
          (if (re-find #"[+-]\d{2}:\d{2}|Z$" s)
            ;; Has timezone pattern but still failed to parse - format error
            (throw (ex-info
                    (str "Invalid datetime format. Expected ISO8601 with timezone, got: " s)
                    {:expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM"
                     :received s
                     :error-type :invalid-format
                     :cause (.getMessage e)}))
            ;; No timezone pattern - missing timezone
            (throw (ex-info
                    "DateTime must include timezone offset"
                    {:received s
                     :expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM"
                     :error-type :missing-timezone})))
          ;; Doesn't look like a datetime at all - format error
          (throw (ex-info
                  (str "Invalid datetime format. Expected ISO8601 with timezone, got: " s)
                  {:expected-format "YYYY-MM-DDTHH:MM:SS±HH:MM"
                   :received s
                   :error-type :invalid-format
                   :cause (.getMessage e)})))))))

;; T047: Serialize datetime function
(defn serialize-datetime
  "Serialize OffsetDateTime or java.util.Date to ISO8601 datetime string with timezone.

  Format: YYYY-MM-DDTHH:MM:SS±HH:MM (approximately 25 characters)
  Example: \"2024-01-15T14:30:00+05:00\"

  Returns string representation with timezone preserved."
  [datetime]
  (cond
    (instance? OffsetDateTime datetime)
    (.format ^OffsetDateTime datetime DateTimeFormatter/ISO_OFFSET_DATE_TIME)

    (instance? java.util.Date datetime)
    ;; Convert java.util.Date to OffsetDateTime (assumes UTC)
    (let [instant (.toInstant ^java.util.Date datetime)
          offset-datetime (.atOffset instant ZoneOffset/UTC)]
      (.format offset-datetime DateTimeFormatter/ISO_OFFSET_DATE_TIME))

    :else
    (throw (ex-info "Invalid datetime type for serialization"
                    {:value datetime :type (type datetime)}))))

;; T048: Store datetime function
(defn store-datetime
  "Convert OffsetDateTime to java.util.Date for Datomic storage.

  Converts the OffsetDateTime to UTC and then to java.util.Date.
  Datomic :db.type/instant requires java.util.Date, not java.time.Instant.
  Note: For timezone preservation, the dual-attribute approach will be
  implemented in the schema generation and mutation layers (T054-T055).

  Returns java.util.Date (UTC)."
  [^OffsetDateTime offset-datetime]
  (java.util.Date/from (.toInstant offset-datetime)))

;; ============================================================================
;; Enum Scalar Functions (T068-T070)
;; ============================================================================

;; T068: Find case-insensitive match helper
(defn find-case-mismatch
  "Find case-insensitive match in valid options.

  Returns the correctly-cased option if a case mismatch is found, nil otherwise."
  [input valid-options]
  (let [input-lower (clojure.string/lower-case (name input))]
    (first (filter #(= input-lower (clojure.string/lower-case (name %)))
                   valid-options))))

;; T069: Validate enum with case-sensitive matching
(defn validate-enum
  "Validate enum value against allowed options (case-sensitive).

  Returns keyword value if valid, throws ex-info with suggestions if invalid.

  Per FR-014: Case-sensitive matching
  Per FR-016: Error messages include valid options and case mismatch suggestions"
  [value allowed-options]
  (let [value-kw (keyword value)]
    (if (contains? (set allowed-options) value-kw)
      value-kw
      (let [suggestion (find-case-mismatch value allowed-options)
            valid-str (clojure.string/join ", " (map name allowed-options))
            msg (if suggestion
                  (format "Invalid value '%s'. Valid options: %s. Did you mean '%s'?"
                          value valid-str (name suggestion))
                  (format "Invalid value '%s'. Valid options: %s"
                          value valid-str))]
        (throw (ex-info msg {:value value
                             :valid-options allowed-options
                             :suggestion suggestion}))))))

;; T070: Serialize enum to string
(defn serialize-enum
  "Serialize keyword enum to string for GraphQL.

  Per FR-015: Serialize enum values as strings in GraphQL responses"
  [kw]
  (name kw))

;; ============================================================================
;; Decimal Scalar Functions (T087-T090)
;; ============================================================================

;; T089: Validate decimal precision
(defn- validate-decimal-precision-scale
  "Validate BigDecimal precision and scale limits.

  Per FR-021: Precision limit 38 digits, scale limit 10 decimal places

  Args:
    bd - BigDecimal to validate
    max-precision - Maximum total digits (default: 38)
    max-scale - Maximum digits after decimal point (default: 10)

  Returns:
    The BigDecimal if valid

  Throws:
    ex-info if precision or scale exceeds limits"
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

;; T087: Parse decimal function
(defn parse-decimal
  "Parse string or number to BigDecimal with precision/scale validation.

  Per FR-017, FR-018, FR-020, FR-021: Support precise decimal values with validation

  Args:
    value - String, number, or BigDecimal to parse
    options - Map with :precision (default 38) and :scale (default 10) limits

  Returns:
    java.math.BigDecimal instance

  Throws:
    ex-info if format is invalid or precision/scale limits exceeded"
  [value {:keys [precision scale] :or {precision 38 scale 10}}]
  (let [bd (cond
             (instance? BigDecimal value) value
             (number? value) (bigdec value)
             (string? value) (try (BigDecimal. ^String value)
                                  (catch NumberFormatException e
                                    (throw (ex-info "Invalid decimal format" {:received value}))))
             :else (throw (ex-info "Cannot parse as Decimal" {:received value})))]
    (validate-decimal-precision-scale bd precision scale)))

;; T088: Serialize decimal function
(defn serialize-decimal
  "Serialize BigDecimal to string for GraphQL.

  Per FR-019: Serialize as string to avoid JavaScript precision loss

  Args:
    bd - BigDecimal to serialize

  Returns:
    String representation with full precision (no scientific notation)"
  [^BigDecimal bd]
  (.toPlainString bd))

