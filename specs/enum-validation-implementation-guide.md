# Enum Validation Implementation Guide

## Step-by-Step Implementation for Ringline

This guide provides a practical, step-by-step approach to implementing case-sensitive enum validation with case mismatch suggestions in the Ringline project.

---

## Step 1: Create the Validation Namespace

**File**: `src/ringline/validation/enum.clj`

```clojure
(ns ringline.validation.enum
  "Case-sensitive enum validation with helpful error messages"
  (:require [clojure.string :as str]
            [malli.core :as m]))

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
```

---

## Step 2: Add Tests

**File**: `test/ringline/validation/enum_test.clj`

```clojure
(ns ringline.validation.enum-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [malli.error :as me]
            [ringline.validation.enum :as enum-val]))

(deftest case-sensitive-enum-test
  (let [schema (enum-val/case-sensitive-enum :active :inactive :pending)]
    
    (testing "exact match validation"
      (is (true? (m/validate schema :active)))
      (is (true? (m/validate schema :inactive)))
      (is (true? (m/validate schema :pending))))
    
    (testing "case mismatch detection"
      (is (false? (m/validate schema :ACTIVE)))
      (is (false? (m/validate schema :Active)))
      (is (false? (m/validate schema :INACTIVE))))
    
    (testing "invalid value detection"
      (is (false? (m/validate schema :unknown)))
      (is (false? (m/validate schema :deleted))))
    
    (testing "case mismatch error messages"
      (let [errors (-> schema (m/explain :ACTIVE) me/humanize)]
        (is (= ["Invalid value :ACTIVE. Did you mean :active?"] errors))))
    
    (testing "invalid value error messages"
      (let [errors (-> schema (m/explain :unknown) me/humanize)]
        (is (= ["Invalid value :unknown. Valid values are: :active, :inactive, :pending"] 
               errors))))))

(deftest validate-enum-test
  (testing "valid value"
    (let [result (enum-val/validate-enum :active [:active :inactive :pending])]
      (is (true? (:valid? result)))
      (is (= :active (:value result)))
      (is (nil? (:error result)))))
  
  (testing "case mismatch"
    (let [result (enum-val/validate-enum :ACTIVE [:active :inactive :pending])]
      (is (false? (:valid? result)))
      (is (= :ACTIVE (:value result)))
      (is (= :active (:suggestion result)))
      (is (clojure.string/includes? (:error result) "Did you mean :active?"))))
  
  (testing "invalid value"
    (let [result (enum-val/validate-enum :unknown [:active :inactive :pending])]
      (is (false? (:valid? result)))
      (is (= :unknown (:value result)))
      (is (nil? (:suggestion result)))
      (is (clojure.string/includes? (:error result) "Valid values are:")))))
```

---

## Step 3: Update Schema Definitions

**Example**: Update user schema to use case-sensitive enum

```clojure
(ns starwars.schema
  (:require [malli.core :as m]
            [ringline.validation.enum :as enum-val]))

;; Before
(def user-schema
  [:map
   [:id :uuid]
   [:name :string]
   [:status [:enum :active :inactive :pending]]])

;; After
(def user-schema
  [:map
   [:id :uuid]
   [:name :string]
   [:status (enum-val/case-sensitive-enum :active :inactive :pending)]])
```

---

## Step 4: Update Mutation Executor

**File**: `src/ringline/mutation/executor.clj`

Update the error handling to use Malli's humanized errors:

```clojure
(ns ringline.mutation.executor
  (:require [malli.core :as m]
            [malli.error :as me]
            [ringline.mutation.parser :as parser]
            [ringline.mutation.transaction :as tx]))

(defn build-validation-error
  "Build a validation error map from Malli explanation"
  [explanation]
  (let [humanized (me/humanize explanation)]
    (if (map? humanized)
      ;; Field-level errors
      (mapv (fn [[field errors]]
              {:code :VALIDATION_ERROR
               :message (first errors)  ; Use custom error message
               :field field})
            humanized)
      ;; Top-level errors
      [{:code :VALIDATION_ERROR
        :message (first humanized)}])))

(defn validate-mutation-input
  "Validate mutation input against schema and operation constraints."
  [mutation-input schema]
  (let [errors (atom [])
        {:keys [operation data entity-id]} mutation-input]
    
    ;; Check if operation is allowed
    (when-not (check-operation-allowed operation schema)
      (swap! errors conj
             {:code :VALIDATION_ERROR
              :message (str "Operation " operation " is not allowed for this entity")}))
    
    ;; Validate input data against derived schema
    (when (and data (#{:create :update} operation))
      (let [input-schema (parser/derive-input-schema schema operation)
            validation-data (if (and (= operation :update) entity-id)
                             (assoc data :id entity-id)
                             data)]
        (when-not (m/validate input-schema validation-data)
          (let [explanation (m/explain input-schema validation-data)
                validation-errors (build-validation-error explanation)]
            (swap! errors concat validation-errors)))))
    
    {:valid? (empty? @errors)
     :errors @errors}))
```

---

## Step 5: Run Tests

```bash
# Run all tests
clj -M:test

# Run specific test
clj -M:test -n ringline.validation.enum-test
```

---

## Step 6: Update Documentation

Add to project README or documentation:

```markdown
### Enum Validation

Ringline provides case-sensitive enum validation with helpful error messages.

When you use an enum value with incorrect casing, you'll get a suggestion:

```clojure
(def user-schema
  [:map
   [:status (enum-val/case-sensitive-enum :active :inactive :pending)]])

(m/validate user-schema {:status :ACTIVE})
;; => false

(-> user-schema
    (m/explain {:status :ACTIVE})
    (me/humanize))
;; => {:status ["Invalid value :ACTIVE. Did you mean :active?"]}
```

For completely invalid values, you'll see all valid options:

```clojure
(-> user-schema
    (m/explain {:status :unknown})
    (me/humanize))
;; => {:status ["Invalid value :unknown. Valid values are: :active, :inactive, :pending"]}
```
```

---

## Step 7: Migration Checklist

- [ ] Create `src/ringline/validation/enum.clj`
- [ ] Create `test/ringline/validation/enum_test.clj`
- [ ] Run tests and verify they pass
- [ ] Update schema definitions to use `case-sensitive-enum`
- [ ] Update mutation executor error handling
- [ ] Test with real mutations
- [ ] Update documentation
- [ ] Review and merge PR

---

## Common Patterns

### Pattern 1: Simple Enum Field

```clojure
[:status (enum-val/case-sensitive-enum :active :inactive :pending)]
```

### Pattern 2: Optional Enum Field

```clojure
[:status {:optional true} (enum-val/case-sensitive-enum :active :inactive :pending)]
```

### Pattern 3: Enum with Default

```clojure
[:status {:default :active} (enum-val/case-sensitive-enum :active :inactive :pending)]
```

### Pattern 4: Multiple Enum Fields

```clojure
[:map
 [:status (enum-val/case-sensitive-enum :active :inactive :pending)]
 [:priority (enum-val/case-sensitive-enum :low :medium :high)]
 [:visibility (enum-val/case-sensitive-enum :public :private)]]
```

---

## Troubleshooting

### Issue: Error messages not showing

**Solution**: Make sure you're using `malli.error/humanize`:

```clojure
(require '[malli.error :as me])

(-> schema
    (m/explain data)
    (me/humanize))  ; Don't forget this!
```

### Issue: Suggestions not working

**Solution**: Verify the enum values are keywords or strings, not mixed types.

### Issue: Performance concerns

**Solution**: For large enums (>100 values), consider caching:

```clojure
(def status-validator
  (let [cached-schema (enum-val/case-sensitive-enum :active :inactive :pending)]
    (m/validator cached-schema)))

;; Reuse the validator
(status-validator :ACTIVE)  ; => false
```

---

## Next Steps

1. Implement the namespace and tests
2. Update existing schemas gradually
3. Monitor error messages in production
4. Gather user feedback
5. Iterate on error message format if needed

