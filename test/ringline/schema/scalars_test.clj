(ns ringline.schema.scalars-test
  "Tests for custom scalar validation and conversion logic."
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.scalars :as scalars])
  (:import [java.time LocalDate OffsetDateTime Instant ZoneOffset]
           [java.math BigDecimal]))

;; ============================================================================
;; Date Scalar Tests (T021-T025)
;; ============================================================================

;; T021: Date scalar parse function (GraphQL → LocalDate)
(deftest parse-date-test
  (testing "Parse valid ISO8601 date string to LocalDate"
    (let [result (scalars/parse-date "2024-01-24")]
      (is (instance? LocalDate result) "Should return LocalDate instance")
      (is (= 2024 (.getYear result)) "Year should be 2024")
      (is (= 1 (.getMonthValue result)) "Month should be January (1)")
      (is (= 24 (.getDayOfMonth result)) "Day should be 24")))

  (testing "Parse date with leading zeros"
    (let [result (scalars/parse-date "2024-01-05")]
      (is (instance? LocalDate result))
      (is (= 5 (.getDayOfMonth result)))))

  (testing "Parse leap year date"
    (let [result (scalars/parse-date "2024-02-29")]
      (is (instance? LocalDate result))
      (is (= 2 (.getMonthValue result)))
      (is (= 29 (.getDayOfMonth result))))))

;; T022: Date scalar serialize function (LocalDate → GraphQL)
(deftest serialize-date-test
  (testing "Serialize LocalDate to ISO8601 string"
    (let [date (LocalDate/of 2024 1 24)
          result (scalars/serialize-date date)]
      (is (string? result) "Should return string")
      (is (= "2024-01-24" result) "Should be ISO8601 format (YYYY-MM-DD)")
      (is (= 10 (count result)) "Should be exactly 10 characters")))

  (testing "Serialize date with single-digit month and day"
    (let [date (LocalDate/of 2024 1 5)
          result (scalars/serialize-date date)]
      (is (= "2024-01-05" result) "Should pad with leading zeros")))

  (testing "Serialize leap year date"
    (let [date (LocalDate/of 2024 2 29)
          result (scalars/serialize-date date)]
      (is (= "2024-02-29" result)))))

;; T023: Date scalar store function (LocalDate → java.util.Date)
(deftest store-date-test
  (testing "Store LocalDate as java.util.Date at midnight UTC"
    (let [date (LocalDate/of 2024 1 24)
          result (scalars/store-date date)]
      (is (instance? java.util.Date result) "Should return java.util.Date")
      ;; Verify it's midnight UTC
      (let [instant (.toInstant result)
            odt (.atOffset instant ZoneOffset/UTC)]
        (is (= 2024 (.getYear odt)))
        (is (= 1 (.getMonthValue odt)))
        (is (= 24 (.getDayOfMonth odt)))
        (is (= 0 (.getHour odt)) "Hour should be 0 (midnight)")
        (is (= 0 (.getMinute odt)) "Minute should be 0")
        (is (= 0 (.getSecond odt)) "Second should be 0"))))

  (testing "Store date preserves date value"
    (let [date (LocalDate/of 2024 12 31)
          result (scalars/store-date date)
          instant (.toInstant result)
          odt (.atOffset instant ZoneOffset/UTC)]
      (is (= 2024 (.getYear odt)))
      (is (= 12 (.getMonthValue odt)))
      (is (= 31 (.getDayOfMonth odt))))))

;; T024: Invalid date format rejection
(deftest parse-date-invalid-format-test
  (testing "Reject invalid date format"
    (is (thrown-with-msg? Exception #"Invalid date format"
          (scalars/parse-date "2024/01/24"))
        "Should reject slash-separated format")

    (is (thrown-with-msg? Exception #"Invalid date format"
          (scalars/parse-date "24-01-2024"))
        "Should reject DD-MM-YYYY format")

    (is (thrown-with-msg? Exception #"Invalid date format"
          (scalars/parse-date "2024-1-24"))
        "Should reject format without leading zeros")

    (is (thrown-with-msg? Exception #"Invalid date format"
          (scalars/parse-date "not-a-date"))
        "Should reject non-date string")

    (is (thrown-with-msg? Exception #"Invalid date format"
          (scalars/parse-date ""))
        "Should reject empty string")))

;; T025: Invalid date values (e.g., Feb 30)
(deftest parse-date-invalid-values-test
  (testing "Reject invalid date values"
    (is (thrown-with-msg? Exception #"Invalid date"
          (scalars/parse-date "2024-02-30"))
        "Should reject Feb 30")

    (is (thrown-with-msg? Exception #"Invalid date"
          (scalars/parse-date "2024-13-01"))
        "Should reject month 13")

    (is (thrown-with-msg? Exception #"Invalid date"
          (scalars/parse-date "2024-00-01"))
        "Should reject month 0")

    (is (thrown-with-msg? Exception #"Invalid date"
          (scalars/parse-date "2024-01-32"))
        "Should reject day 32")

    (is (thrown-with-msg? Exception #"Invalid date"
          (scalars/parse-date "2023-02-29"))
        "Should reject Feb 29 in non-leap year")))

;; ============================================================================
;; DateTime Scalar Tests (T039-T045)
;; ============================================================================

;; T039: DateTime scalar parse function (GraphQL → OffsetDateTime)
(deftest parse-datetime-test
  (testing "Parse valid ISO8601 datetime string with timezone to OffsetDateTime"
    (let [result (scalars/parse-datetime "2024-01-24T14:30:00+05:00")]
      (is (instance? OffsetDateTime result) "Should return OffsetDateTime instance")
      (is (= 2024 (.getYear result)) "Year should be 2024")
      (is (= 1 (.getMonthValue result)) "Month should be January (1)")
      (is (= 24 (.getDayOfMonth result)) "Day should be 24")
      (is (= 14 (.getHour result)) "Hour should be 14")
      (is (= 30 (.getMinute result)) "Minute should be 30")
      (is (= 0 (.getSecond result)) "Second should be 0")
      (is (= "+05:00" (str (.getOffset result))) "Timezone offset should be +05:00")))

  (testing "Parse datetime with Z (UTC) timezone"
    (let [result (scalars/parse-datetime "2024-01-24T14:30:00Z")]
      (is (instance? OffsetDateTime result))
      (is (= "Z" (str (.getOffset result))) "Timezone offset should be Z (UTC)")))

  (testing "Parse datetime with negative timezone offset"
    (let [result (scalars/parse-datetime "2024-01-24T14:30:00-08:00")]
      (is (instance? OffsetDateTime result))
      (is (= "-08:00" (str (.getOffset result))) "Timezone offset should be -08:00")))

  (testing "Parse datetime with seconds"
    (let [result (scalars/parse-datetime "2024-01-24T14:30:45+00:00")]
      (is (= 45 (.getSecond result)) "Second should be 45"))))

;; T040: DateTime scalar serialize function (OffsetDateTime → GraphQL)
(deftest serialize-datetime-test
  (testing "Serialize OffsetDateTime to ISO8601 string with timezone"
    (let [odt (OffsetDateTime/of 2024 1 24 14 30 0 0 (java.time.ZoneOffset/ofHours 5))
          result (scalars/serialize-datetime odt)]
      (is (string? result) "Should return string")
      (is (= "2024-01-24T14:30:00+05:00" result) "Should be ISO8601 format with timezone")
      (is (= 25 (count result)) "Should be exactly 25 characters")))

  (testing "Serialize datetime with UTC timezone"
    (let [odt (OffsetDateTime/of 2024 1 24 14 30 0 0 java.time.ZoneOffset/UTC)
          result (scalars/serialize-datetime odt)]
      (is (= "2024-01-24T14:30:00Z" result) "Should use Z for UTC")))

  (testing "Serialize datetime with negative offset"
    (let [odt (OffsetDateTime/of 2024 1 24 14 30 0 0 (java.time.ZoneOffset/ofHours -8))
          result (scalars/serialize-datetime odt)]
      (is (= "2024-01-24T14:30:00-08:00" result))))

  (testing "Serialize datetime with seconds"
    (let [odt (OffsetDateTime/of 2024 1 24 14 30 45 0 java.time.ZoneOffset/UTC)
          result (scalars/serialize-datetime odt)]
      (is (= "2024-01-24T14:30:45Z" result)))))

;; T041: DateTime scalar store function (OffsetDateTime → java.util.Date)
(deftest store-datetime-test
  (testing "Store OffsetDateTime as java.util.Date (UTC)"
    (let [odt (OffsetDateTime/of 2024 1 24 14 30 0 0 (java.time.ZoneOffset/ofHours 5))
          result (scalars/store-datetime odt)]
      (is (instance? java.util.Date result) "Should return java.util.Date")
      ;; Verify it's converted to UTC (14:30 +05:00 = 09:30 UTC)
      (let [instant (.toInstant result)
            utc-odt (.atOffset instant ZoneOffset/UTC)]
        (is (= 2024 (.getYear utc-odt)))
        (is (= 1 (.getMonthValue utc-odt)))
        (is (= 24 (.getDayOfMonth utc-odt)))
        (is (= 9 (.getHour utc-odt)) "Hour should be 9 (14-5)")
        (is (= 30 (.getMinute utc-odt))))))

  (testing "Store datetime preserves instant in time"
    (let [odt (OffsetDateTime/of 2024 1 24 0 0 0 0 (java.time.ZoneOffset/ofHours -8))
          result (scalars/store-datetime odt)
          instant (.toInstant result)
          utc-odt (.atOffset instant ZoneOffset/UTC)]
      ;; 00:00 -08:00 = 08:00 UTC
      (is (= 8 (.getHour utc-odt))))))

;; T042: Validate datetime has timezone
(deftest validate-datetime-has-timezone-test
  (testing "Accept datetime with positive timezone offset"
    (is (nil? (scalars/validate-datetime-has-timezone "2024-01-24T14:30:00+05:00"))
        "Should accept datetime with +HH:MM offset"))

  (testing "Accept datetime with negative timezone offset"
    (is (nil? (scalars/validate-datetime-has-timezone "2024-01-24T14:30:00-08:00"))
        "Should accept datetime with -HH:MM offset"))

  (testing "Accept datetime with Z (UTC)"
    (is (nil? (scalars/validate-datetime-has-timezone "2024-01-24T14:30:00Z"))
        "Should accept datetime with Z timezone"))

  (testing "Reject datetime without timezone"
    (is (thrown-with-msg? Exception #"must include timezone"
          (scalars/validate-datetime-has-timezone "2024-01-24T14:30:00"))
        "Should reject datetime without timezone offset")))

;; T043: Invalid datetime format rejection
(deftest parse-datetime-invalid-format-test
  (testing "Reject datetime without timezone"
    (is (thrown-with-msg? Exception #"must include timezone"
          (scalars/parse-datetime "2024-01-24T14:30:00"))
        "Should reject datetime without timezone"))

  (testing "Reject invalid datetime format"
    (is (thrown-with-msg? Exception #"Invalid datetime format"
          (scalars/parse-datetime "2024-01-24 14:30:00+00:00"))
        "Should reject space-separated format")

    (is (thrown-with-msg? Exception #"Invalid datetime format"
          (scalars/parse-datetime "2024/01/24T14:30:00+00:00"))
        "Should reject slash-separated date")

    (is (thrown-with-msg? Exception #"Invalid datetime format"
          (scalars/parse-datetime "not-a-datetime"))
        "Should reject non-datetime string")))

;; T044: Invalid datetime values
(deftest parse-datetime-invalid-values-test
  (testing "Reject invalid time values"
    (is (thrown-with-msg? Exception #"Invalid datetime"
          (scalars/parse-datetime "2024-01-24T25:00:00+00:00"))
        "Should reject hour 25")

    (is (thrown-with-msg? Exception #"Invalid datetime"
          (scalars/parse-datetime "2024-01-24T14:60:00+00:00"))
        "Should reject minute 60")

    (is (thrown-with-msg? Exception #"Invalid datetime"
          (scalars/parse-datetime "2024-01-24T14:30:60+00:00"))
        "Should reject second 60"))

  (testing "Reject invalid timezone offset"
    (is (thrown-with-msg? Exception #"Invalid datetime"
          (scalars/parse-datetime "2024-01-24T14:30:00+99:99"))
        "Should reject invalid timezone offset")))

;; ============================================================================
;; Enum Scalar Tests (T061-T067)
;; ============================================================================

;; T061: Enum validation - valid values
(deftest validate-enum-valid-test
  (testing "Accept valid enum values (case-sensitive)"
    (let [valid-options [:draft :in-progress :completed :cancelled]]
      (is (= :draft (scalars/validate-enum "draft" valid-options))
          "Should accept exact match")
      (is (= :in-progress (scalars/validate-enum "in-progress" valid-options))
          "Should accept hyphenated value")
      (is (= :completed (scalars/validate-enum "completed" valid-options))
          "Should accept another valid value")
      (is (= :cancelled (scalars/validate-enum "cancelled" valid-options))
          "Should accept all defined options"))))

;; T062: Enum validation - invalid values
(deftest validate-enum-invalid-test
  (testing "Reject invalid enum values with error message"
    (let [valid-options [:draft :in-progress :completed :cancelled]]
      (is (thrown-with-msg? Exception #"Invalid value 'unknown'"
            (scalars/validate-enum "unknown" valid-options))
          "Should reject undefined value")

      (is (thrown-with-msg? Exception #"Valid options:"
            (scalars/validate-enum "unknown" valid-options))
          "Error message should list valid options"))))

;; T063: Enum validation - case sensitivity
(deftest validate-enum-case-sensitive-test
  (testing "Enum validation is case-sensitive"
    (let [valid-options [:draft :in-progress :completed]]
      ;; Should reject wrong case
      (is (thrown? Exception (scalars/validate-enum "Draft" valid-options))
          "Should reject capitalized value")

      (is (thrown? Exception (scalars/validate-enum "DRAFT" valid-options))
          "Should reject uppercase value")

      (is (thrown? Exception (scalars/validate-enum "IN-PROGRESS" valid-options))
          "Should reject uppercase hyphenated value"))))

;; T064: Enum validation - case mismatch suggestions
(deftest validate-enum-case-mismatch-suggestion-test
  (testing "Provide suggestions for case mismatches"
    (let [valid-options [:draft :in-progress :completed :cancelled]]
      ;; Test capitalized version
      (try
        (scalars/validate-enum "Draft" valid-options)
        (is false "Should have thrown exception")
        (catch Exception e
          (is (re-find #"Did you mean 'draft'" (.getMessage e))
              "Should suggest lowercase version")))

      ;; Test uppercase version
      (try
        (scalars/validate-enum "COMPLETED" valid-options)
        (is false "Should have thrown exception")
        (catch Exception e
          (is (re-find #"Did you mean 'completed'" (.getMessage e))
              "Should suggest lowercase version")))

      ;; Test hyphenated uppercase
      (try
        (scalars/validate-enum "IN-PROGRESS" valid-options)
        (is false "Should have thrown exception")
        (catch Exception e
          (is (re-find #"Did you mean 'in-progress'" (.getMessage e))
              "Should suggest correct case for hyphenated value"))))))

;; T065: Enum validation - no suggestion for completely different values
(deftest validate-enum-no-suggestion-test
  (testing "No suggestion for values that don't match any option"
    (let [valid-options [:draft :in-progress :completed]]
      (try
        (scalars/validate-enum "unknown" valid-options)
        (is false "Should have thrown exception")
        (catch Exception e
          (is (not (re-find #"Did you mean" (.getMessage e)))
              "Should not suggest when no case mismatch")
          (is (re-find #"Valid options:" (.getMessage e))
              "Should still list valid options"))))))

;; T066: Serialize enum to string
(deftest serialize-enum-test
  (testing "Serialize keyword enum to string for GraphQL"
    (is (= "draft" (scalars/serialize-enum :draft))
        "Should convert keyword to string")
    (is (= "in-progress" (scalars/serialize-enum :in-progress))
        "Should preserve hyphens")
    (is (= "completed" (scalars/serialize-enum :completed))
        "Should work for any keyword")))

;; T067: Find case mismatch helper
(deftest find-case-mismatch-test
  (testing "Find case-insensitive match in valid options"
    (let [valid-options [:draft :in-progress :completed]]
      (is (= :draft (scalars/find-case-mismatch "Draft" valid-options))
          "Should find capitalized match")
      (is (= :draft (scalars/find-case-mismatch "DRAFT" valid-options))
          "Should find uppercase match")
      (is (= :in-progress (scalars/find-case-mismatch "IN-PROGRESS" valid-options))
          "Should find hyphenated uppercase match")
      (is (nil? (scalars/find-case-mismatch "unknown" valid-options))
          "Should return nil for no match"))))

;; ============================================================================
;; Decimal Scalar Tests (T080-T086)
;; ============================================================================

;; T080: Decimal parse function - valid values
(deftest parse-decimal-valid-test
  (testing "Parse valid decimal strings and numbers to BigDecimal"
    ;; Parse string values
    (let [result (scalars/parse-decimal "19.99" {:precision 38 :scale 10})]
      (is (instance? BigDecimal result) "Should return BigDecimal")
      (is (= (bigdec "19.99") result) "Should parse correctly"))

    ;; Parse integer string
    (let [result (scalars/parse-decimal "100" {:precision 38 :scale 10})]
      (is (instance? BigDecimal result) "Should return BigDecimal")
      (is (= (bigdec "100") result) "Should parse integer"))

    ;; Parse number with many decimal places
    (let [result (scalars/parse-decimal "1234567890.1234567890" {:precision 38 :scale 10})]
      (is (instance? BigDecimal result) "Should return BigDecimal")
      (is (= (bigdec "1234567890.1234567890") result) "Should preserve precision"))

    ;; Parse very small number
    (let [result (scalars/parse-decimal "0.0000000001" {:precision 38 :scale 10})]
      (is (instance? BigDecimal result) "Should return BigDecimal")
      (is (= (bigdec "0.0000000001") result) "Should handle small decimals"))

    ;; Parse number (not string)
    (let [result (scalars/parse-decimal 42.5 {:precision 38 :scale 10})]
      (is (instance? BigDecimal result) "Should accept number input"))

    ;; Parse BigDecimal (pass through)
    (let [bd (bigdec "99.99")
          result (scalars/parse-decimal bd {:precision 38 :scale 10})]
      (is (= bd result) "Should pass through BigDecimal"))))

;; T081: Decimal serialize function
(deftest serialize-decimal-test
  (testing "Serialize BigDecimal to string for GraphQL"
    (is (= "19.99" (scalars/serialize-decimal (bigdec "19.99")))
        "Should convert to string")
    (is (= "100" (scalars/serialize-decimal (bigdec "100")))
        "Should handle integers")
    (is (= "1234567890.1234567890" (scalars/serialize-decimal (bigdec "1234567890.1234567890")))
        "Should preserve precision in string")
    (is (= "0.0000000001" (scalars/serialize-decimal (bigdec "0.0000000001")))
        "Should handle small decimals")))

;; T082: Decimal precision validation (38 digits max)
(deftest decimal-precision-validation-test
  (testing "Reject decimal values exceeding precision limit"
    ;; Valid: 38 digits total
    (let [valid-38 "12345678901234567890123456789012345678"]
      (is (instance? BigDecimal (scalars/parse-decimal valid-38 {:precision 38 :scale 10}))
          "Should accept 38 digits"))

    ;; Invalid: 39 digits total
    (let [invalid-39 "123456789012345678901234567890123456789"]
      (is (thrown-with-msg? Exception #"precision exceeds limit"
            (scalars/parse-decimal invalid-39 {:precision 38 :scale 10}))
          "Should reject 39 digits"))

    ;; Error message should include limits
    (try
      (scalars/parse-decimal "123456789012345678901234567890123456789" {:precision 38 :scale 10})
      (is false "Should have thrown exception")
      (catch Exception e
        (is (re-find #"38" (.getMessage e)) "Error should mention limit")
        (is (re-find #"39" (.getMessage e)) "Error should mention actual value")))))

;; T083: Decimal scale validation (10 decimal places max)
(deftest decimal-scale-validation-test
  (testing "Reject decimal values exceeding scale limit"
    ;; Valid: 10 decimal places
    (let [valid-10 "1.1234567890"]
      (is (instance? BigDecimal (scalars/parse-decimal valid-10 {:precision 38 :scale 10}))
          "Should accept 10 decimal places"))

    ;; Invalid: 11 decimal places
    (let [invalid-11 "1.12345678901"]
      (is (thrown-with-msg? Exception #"scale exceeds limit"
            (scalars/parse-decimal invalid-11 {:precision 38 :scale 10}))
          "Should reject 11 decimal places"))

    ;; Error message should include limits
    (try
      (scalars/parse-decimal "1.12345678901" {:precision 38 :scale 10})
      (is false "Should have thrown exception")
      (catch Exception e
        (is (re-find #"10" (.getMessage e)) "Error should mention limit")
        (is (re-find #"11" (.getMessage e)) "Error should mention actual value")))))

;; T084: Decimal precision preservation
(deftest decimal-precision-preservation-test
  (testing "Maintain full precision through parse and serialize"
    (let [test-values ["19.99"
                       "1234567890.1234567890"
                       "0.0000000001"
                       "999999999999999999999999999.9999999999"]]
      (doseq [value test-values]
        (let [parsed (scalars/parse-decimal value {:precision 38 :scale 10})
              serialized (scalars/serialize-decimal parsed)]
          (is (= value serialized)
              (str "Should preserve precision for " value)))))))

;; T085: Invalid decimal format rejection
(deftest decimal-invalid-format-test
  (testing "Reject invalid decimal formats"
    ;; Invalid string format
    (is (thrown-with-msg? Exception #"Invalid decimal format"
          (scalars/parse-decimal "not-a-number" {:precision 38 :scale 10}))
        "Should reject non-numeric string")

    (is (thrown-with-msg? Exception #"Invalid decimal format"
          (scalars/parse-decimal "12.34.56" {:precision 38 :scale 10}))
        "Should reject multiple decimal points")

    ;; Invalid type
    (is (thrown-with-msg? Exception #"Cannot parse as Decimal"
          (scalars/parse-decimal nil {:precision 38 :scale 10}))
        "Should reject nil")

    (is (thrown-with-msg? Exception #"Cannot parse as Decimal"
          (scalars/parse-decimal {:not "a number"} {:precision 38 :scale 10}))
        "Should reject map")))

