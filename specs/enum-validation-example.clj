(ns enum-validation-example
  "Practical examples of case-sensitive enum validation with case mismatch suggestions"
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn- normalize-value
  "Convert value to string for comparison"
  [value]
  (cond
    (keyword? value) (name value)
    (string? value) value
    :else (str value)))

(defn- find-case-match
  "Find enum value that matches case-insensitively"
  [value enum-values]
  (let [value-lower (str/lower-case (normalize-value value))]
    (first (filter (fn [enum-val]
                     (= value-lower (str/lower-case (normalize-value enum-val))))
                   enum-values))))

;; ============================================================================
;; Approach 1: Simple Case Mismatch Detection
;; ============================================================================

(defn case-mismatch?
  "Check if value matches any enum value case-insensitively but not exactly"
  [value enum-values]
  (let [value-str (normalize-value value)
        value-lower (str/lower-case value-str)]
    (some (fn [enum-val]
            (let [enum-str (normalize-value enum-val)
                  enum-lower (str/lower-case enum-str)]
              (and (= value-lower enum-lower)
                   (not= value-str enum-str))))
          enum-values)))

;; ============================================================================
;; Approach 2: Validation with Detailed Results
;; ============================================================================

(defn validate-enum
  "Validate a value against enum values and return detailed result.
  
  Returns:
    {:valid? boolean
     :value original-value
     :error optional error message
     :suggestion optional suggested value}"
  [value enum-values]
  (let [enum-set (set enum-values)]
    (if (contains? enum-set value)
      {:valid? true
       :value value}
      (let [case-match (find-case-match value enum-values)]
        {:valid? false
         :value value
         :error (if case-match
                 (str "Invalid value " (pr-str value) 
                      ". Did you mean " (pr-str case-match) "?")
                 (str "Invalid value " (pr-str value) 
                      ". Valid values are: " 
                      (str/join ", " (map pr-str enum-values))))
         :suggestion case-match}))))

;; ============================================================================
;; Approach 3: Malli Schema with Custom Error Messages
;; ============================================================================

(defn case-sensitive-enum
  "Create a case-sensitive enum schema with case mismatch suggestions.
  
  Example:
    (def status-schema (case-sensitive-enum :active :inactive :pending))
    (m/validate status-schema :ACTIVE) ;; => false
    (me/humanize (m/explain status-schema :ACTIVE))
    ;; => [\"Invalid value :ACTIVE. Did you mean :active?\"]"
  [& enum-values]
  (let [enum-set (set enum-values)]
    [:fn
     {:error/fn (fn [{:keys [value]}]
                  (let [case-match (find-case-match value enum-values)]
                    (if case-match
                      (str "Invalid value " (pr-str value) 
                           ". Did you mean " (pr-str case-match) "?")
                      (str "Invalid value " (pr-str value) 
                           ". Valid values are: " 
                           (str/join ", " (map pr-str enum-values))))))}
     (fn [value] (contains? enum-set value))]))

;; ============================================================================
;; Approach 4: Optimized with Caching
;; ============================================================================

(defn optimized-enum-validator
  "Create an optimized enum validator with cached lowercase values.
  
  This approach pre-computes lowercase versions for O(1) lookup."
  [enum-values]
  (let [enum-set (set enum-values)
        lowercase-map (into {} (map (fn [v]
                                      [(str/lower-case (normalize-value v)) v])
                                    enum-values))]
    (fn [value]
      (cond
        ;; Exact match - fast path
        (contains? enum-set value)
        {:valid? true :value value}
        
        ;; Case mismatch - check cached lowercase map
        :else
        (let [value-lower (str/lower-case (normalize-value value))
              case-match (get lowercase-map value-lower)]
          (if case-match
            {:valid? false
             :value value
             :error (str "Invalid value " (pr-str value) ". Did you mean " (pr-str case-match) "?")
             :suggestion case-match}
            {:valid? false
             :value value
             :error (str "Invalid value " (pr-str value) ". Valid values are: " 
                        (str/join ", " (map pr-str enum-values)))}))))))

;; ============================================================================
;; Examples and Tests
;; ============================================================================

(comment
  ;; Example 1: Simple case mismatch detection
  (case-mismatch? :ACTIVE [:active :inactive :pending])
  ;; => true
  
  (case-mismatch? :active [:active :inactive :pending])
  ;; => false (exact match)
  
  (case-mismatch? :unknown [:active :inactive :pending])
  ;; => false (no match)
  
  ;; Example 2: Detailed validation
  (validate-enum :active [:active :inactive :pending])
  ;; => {:valid? true, :value :active}
  
  (validate-enum :ACTIVE [:active :inactive :pending])
  ;; => {:valid? false
  ;;     :value :ACTIVE
  ;;     :error "Invalid value :ACTIVE. Did you mean :active?"
  ;;     :suggestion :active}
  
  (validate-enum :unknown [:active :inactive :pending])
  ;; => {:valid? false
  ;;     :value :unknown
  ;;     :error "Invalid value :unknown. Valid values are: :active, :inactive, :pending"
  ;;     :suggestion nil}
  
  ;; Example 3: Malli schema
  (def status-schema (case-sensitive-enum :active :inactive :pending))
  
  (m/validate status-schema :active)
  ;; => true
  
  (m/validate status-schema :ACTIVE)
  ;; => false
  
  (-> status-schema
      (m/explain :ACTIVE)
      (me/humanize))
  ;; => ["Invalid value :ACTIVE. Did you mean :active?"]
  
  (-> status-schema
      (m/explain :unknown)
      (me/humanize))
  ;; => ["Invalid value :unknown. Valid values are: :active, :inactive, :pending"]
  
  ;; Example 4: Optimized validator
  (def validate-status (optimized-enum-validator [:active :inactive :pending]))
  
  (validate-status :active)
  ;; => {:valid? true, :value :active}
  
  (validate-status :ACTIVE)
  ;; => {:valid? false
  ;;     :value :ACTIVE
  ;;     :error "Invalid value :ACTIVE. Did you mean :active?"
  ;;     :suggestion :active}
  
  ;; Example 5: Using in a map schema
  (def user-schema
    [:map
     [:id :uuid]
     [:name :string]
     [:status (case-sensitive-enum :active :inactive :pending)]])
  
  (m/validate user-schema
              {:id #uuid "123e4567-e89b-12d3-a456-426614174000"
               :name "Alice"
               :status :active})
  ;; => true
  
  (-> user-schema
      (m/explain {:id #uuid "123e4567-e89b-12d3-a456-426614174000"
                  :name "Alice"
                  :status :ACTIVE})
      (me/humanize))
  ;; => {:status ["Invalid value :ACTIVE. Did you mean :active?"]}
  
  )

