(ns ringline.research.malli-type-examples
  "Code examples demonstrating Malli type support for Date, DateTime, Decimal, and Enum types.
   
   This file contains working examples based on research into Malli's capabilities.
   See research-findings.md for detailed explanations."
  (:require [malli.core :as m]
            [malli.registry :as mr]
            [malli.experimental.time :as met]
            [malli.experimental.time.transform :as mett]
            [malli.transform :as mt])
  (:import [java.time LocalDate OffsetDateTime]
           [java.math BigDecimal]))

;; =============================================================================
;; SETUP: Registry Configuration
;; =============================================================================

(comment
  ;; Add experimental time schemas to default registry
  (mr/set-default-registry!
    (mr/composite-registry
      (m/default-schemas)
      (met/schemas)))
  
  ;; Verify time schemas are available
  (-> (m/default-schemas) keys (filter #(= "time" (namespace %))))
  ;; => (:time/duration :time/instant :time/local-date :time/local-date-time
  ;;     :time/local-time :time/offset-date-time :time/offset-time
  ;;     :time/zone-id :time/zone-offset :time/zoned-date-time)
  )

;; =============================================================================
;; EXAMPLE 1: Date-Only Type (LocalDate)
;; =============================================================================

(def date-schema
  "Schema for date-only fields (no time component)"
  [:map
   [:birth-date :time/local-date]
   [:hire-date [:time/local-date {:min (LocalDate/parse "2020-01-01")}]]])

(comment
  ;; Validation
  (m/validate date-schema
              {:birth-date (LocalDate/parse "1990-05-15")
               :hire-date (LocalDate/parse "2021-03-01")})
  ;; => true
  
  ;; String to LocalDate transformation
  (m/decode date-schema
            {:birth-date "1990-05-15"
             :hire-date "2021-03-01"}
            (mett/time-transformer))
  ;; => {:birth-date #object[java.time.LocalDate "1990-05-15"]
  ;;     :hire-date #object[java.time.LocalDate "2021-03-01"]}
  
  ;; LocalDate to String transformation
  (m/encode date-schema
            {:birth-date (LocalDate/parse "1990-05-15")
             :hire-date (LocalDate/parse "2021-03-01")}
            (mett/time-transformer))
  ;; => {:birth-date "1990-05-15", :hire-date "2021-03-01"}
  
  ;; Custom date format
  (m/decode [:time/local-date {:pattern "yyyyMMdd"}]
            "19900515"
            (mett/time-transformer))
  ;; => #object[java.time.LocalDate "1990-05-15"]
  )

;; =============================================================================
;; EXAMPLE 2: DateTime with Timezone (OffsetDateTime)
;; =============================================================================

(def datetime-schema
  "Schema for datetime fields with timezone offset"
  [:map
   [:created-at :time/offset-date-time]
   [:updated-at [:time/offset-date-time {:optional true}]]])

(comment
  ;; Validation
  (m/validate datetime-schema
              {:created-at (OffsetDateTime/parse "2024-01-24T10:30:00-05:00")})
  ;; => true
  
  ;; String to OffsetDateTime transformation
  (m/decode datetime-schema
            {:created-at "2024-01-24T10:30:00-05:00"}
            (mett/time-transformer))
  ;; => {:created-at #object[java.time.OffsetDateTime "2024-01-24T10:30-05:00"]}
  
  ;; OffsetDateTime to String transformation
  (m/encode datetime-schema
            {:created-at (OffsetDateTime/parse "2024-01-24T10:30:00-05:00")}
            (mett/time-transformer))
  ;; => {:created-at "2024-01-24T10:30:00-05:00"}
  
  ;; ISO 8601 format validation (typical length: 25-30 characters)
  (count "2024-01-24T10:30:00-05:00")
  ;; => 25
  )

;; =============================================================================
;; EXAMPLE 3: Decimal Type (Custom Schema)
;; =============================================================================

(defn -decimal-schema
  "Custom schema for BigDecimal values with optional precision/scale validation"
  []
  ^{:type ::m/into-schema}
  (reify m/IntoSchema
    (-type [_] :decimal)
    (-type-properties [_] nil)
    (-properties-schema [_ _] nil)
    (-children-schema [_ _] nil)
    (-into-schema [parent properties children options]
      (let [{:keys [precision scale]} properties]
        ^{:type ::m/schema}
        (reify m/Schema
          (-validator [_]
            (fn [x]
              (and (instance? BigDecimal x)
                   (or (nil? precision)
                       (<= (.precision ^BigDecimal x) precision))
                   (or (nil? scale)
                       (<= (.scale ^BigDecimal x) scale)))))
          (-explainer [this path]
            (fn explain [x in acc]
              (cond
                (not (instance? BigDecimal x))
                (conj acc (m/-error path in this x ::not-decimal))
                
                (and precision (> (.precision ^BigDecimal x) precision))
                (conj acc (m/-error path in this x ::precision-exceeded))
                
                (and scale (> (.scale ^BigDecimal x) scale))
                (conj acc (m/-error path in this x ::scale-exceeded))
                
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

(def decimal-transformer
  "Transformer for string <-> BigDecimal conversion"
  (mt/transformer
    {:name :decimal
     :decoders {:decimal (fn [x]
                           (cond
                             (instance? BigDecimal x) x
                             (string? x) (BigDecimal. ^String x)
                             (number? x) (BigDecimal/valueOf (double x))
                             :else x))}
     :encoders {:decimal (fn [x]
                           (if (instance? BigDecimal x)
                             (str x)
                             x))}}))

(comment
  ;; Register custom decimal schema
  (mr/set-default-registry!
    (mr/composite-registry
      (m/default-schemas)
      (met/schemas)
      {:decimal (-decimal-schema)}))
  
  ;; Validation
  (m/validate :decimal (BigDecimal. "123.45"))
  ;; => true
  
  (m/validate [:decimal {:scale 2}] (BigDecimal. "123.45"))
  ;; => true
  
  (m/validate [:decimal {:scale 2}] (BigDecimal. "123.456"))
  ;; => false (scale exceeded)
  
  ;; String to BigDecimal transformation
  (m/decode :decimal "123.45" decimal-transformer)
  ;; => #object[java.math.BigDecimal "123.45"]
  
  ;; BigDecimal to String transformation
  (m/encode :decimal (BigDecimal. "123.45") decimal-transformer)
  ;; => "123.45"
  )

;; =============================================================================
;; EXAMPLE 4: Enum Type (Built-in)
;; =============================================================================

(def enum-schema
  "Schema with enum fields"
  [:map
   [:status [:enum :draft :published :archived]]
   [:priority [:enum :low :medium :high :urgent]]])

(comment
  ;; Validation
  (m/validate enum-schema
              {:status :published
               :priority :high})
  ;; => true
  
  (m/validate enum-schema
              {:status :invalid
               :priority :high})
  ;; => false
  
  ;; Error explanation
  (m/explain enum-schema
             {:status :invalid
              :priority :high})
  ;; => {:schema [:map ...],
  ;;     :value {:status :invalid, :priority :high},
  ;;     :errors [{:path [:status],
  ;;               :in [:status],
  ;;               :schema [:enum :draft :published :archived],
  ;;               :value :invalid}]}
  
  ;; Extract enum values for GraphQL enum generation
  (-> [:enum :draft :published :archived]
      m/schema
      m/children)
  ;; => [:draft :published :archived]
  )

;; =============================================================================
;; COMPLETE EXAMPLE: Product Schema
;; =============================================================================

(def product-schema
  "Complete schema demonstrating all custom scalar types"
  [:map
   {:ringline/datomic-ns "product"}
   [:id :uuid]
   [:name :string]
   [:description {:optional true} :string]
   [:price [:decimal {:scale 2}]]
   [:status [:enum :draft :published :archived]]
   [:created-at :time/offset-date-time]
   [:updated-at {:optional true} :time/offset-date-time]
   [:launch-date {:optional true} :time/local-date]])

(comment
  ;; Validate complete product
  (m/validate product-schema
              {:id (java.util.UUID/randomUUID)
               :name "Widget Pro"
               :price (BigDecimal. "99.99")
               :status :published
               :created-at (OffsetDateTime/parse "2024-01-24T10:30:00-05:00")
               :launch-date (LocalDate/parse "2024-02-01")})
  ;; => true
  
  ;; Decode from GraphQL input (strings)
  (m/decode product-schema
            {:id "550e8400-e29b-41d4-a716-446655440000"
             :name "Widget Pro"
             :price "99.99"
             :status :published
             :created-at "2024-01-24T10:30:00-05:00"
             :launch-date "2024-02-01"}
            (mt/transformer
              (mt/string-transformer)
              (mett/time-transformer)
              decimal-transformer))
  ;; => {:id #uuid "550e8400-e29b-41d4-a716-446655440000",
  ;;     :name "Widget Pro",
  ;;     :price #object[java.math.BigDecimal "99.99"],
  ;;     :status :published,
  ;;     :created-at #object[java.time.OffsetDateTime "2024-01-24T10:30-05:00"],
  ;;     :launch-date #object[java.time.LocalDate "2024-02-01"]}
  )

