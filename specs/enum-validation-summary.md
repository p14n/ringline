# Enum Validation Research - Executive Summary

## Quick Answers to Research Questions

### 1. How to detect case mismatches?

**Answer**: Use case-insensitive string comparison

```clojure
(defn case-mismatch? [value enum-values]
  (let [value-lower (clojure.string/lower-case (name value))]
    (some #(and (= value-lower (clojure.string/lower-case (name %)))
                (not= value %))
          enum-values)))
```

**Complexity**: O(n) where n = number of enum values  
**Performance**: Excellent for typical enum sizes (< 100 values)

---

### 2. What algorithms can suggest similar values?

**Recommended**: Case-insensitive comparison (simpler and faster)

```clojure
(defn find-case-match [value enum-values]
  (let [value-lower (clojure.string/lower-case (name value))]
    (first (filter #(= value-lower (clojure.string/lower-case (name %)))
                   enum-values))))
```

**Alternative**: Levenshtein distance (via `clj-fuzzy` library)

```clojure
;; deps.edn
{:deps {clj-fuzzy/clj-fuzzy {:mvn/version "0.4.1"}}}

;; Usage
(require '[clj-fuzzy.metrics :as metrics])
(metrics/levenshtein "activ" "active") ;; => 1
```

**Recommendation**: Use case-insensitive matching for enums. Levenshtein distance adds complexity without significant benefit for this use case.

---

### 3. How to format error messages with suggestions?

**Best Practice Format**:

```
Invalid value :ACTIVE for field :status. 
Did you mean :active? 
Valid values are: :active, :inactive, :pending
```

**Implementation**:

```clojure
(defn format-enum-error [value enum-values]
  (let [case-match (find-case-match value enum-values)]
    (str "Invalid value " (pr-str value) ". "
         (when case-match
           (str "Did you mean " (pr-str case-match) "? "))
         "Valid values are: " 
         (clojure.string/join ", " (map pr-str enum-values)))))
```

**Key Principles**:
- Be specific about what's wrong
- Provide actionable suggestions
- Keep it concise
- Show the invalid value and valid options

---

### 4. Performance considerations?

**Case Mismatch Detection**:
- **Complexity**: O(n) linear scan
- **Optimization**: Cache lowercase versions for O(1) lookup
- **Recommendation**: Fine for enums with < 100 values

**Optimized Implementation**:

```clojure
(defn optimized-validator [enum-values]
  (let [enum-set (set enum-values)
        lowercase-map (into {} (map (fn [v]
                                      [(clojure.string/lower-case (name v)) v])
                                    enum-values))]
    (fn [value]
      (or (contains? enum-set value)  ; O(1) exact match
          (get lowercase-map (clojure.string/lower-case (name value)))))))  ; O(1) case match
```

**Performance Tips**:
1. Short-circuit on exact match before checking case mismatch
2. Cache lowercase versions of enum values
3. Use sets for O(1) membership testing
4. Limit suggestions to top 3 most similar values

---

### 5. Should we use Malli's :enum type or custom validation?

**Answer**: Use Malli's `:enum` type wrapped with custom error messages

**Recommended Approach**:

```clojure
(defn case-sensitive-enum [& enum-values]
  [:fn
   {:error/fn (fn [{:keys [value]}]
                (let [case-match (find-case-match value enum-values)]
                  (if case-match
                    (str "Invalid value " (pr-str value) 
                         ". Did you mean " (pr-str case-match) "?")
                    (str "Invalid value " (pr-str value) 
                         ". Valid values are: " 
                         (clojure.string/join ", " (map pr-str enum-values))))))}
   (fn [value] (contains? (set enum-values) value))])
```

**Rationale**:
- ✅ Leverages Malli's validation infrastructure
- ✅ Provides custom error messages via `:error/fn`
- ✅ Integrates seamlessly with existing Malli schemas
- ✅ Supports all Malli features (explain, humanize, etc.)
- ✅ Minimal performance overhead

---

## Implementation Recommendations

### For Ringline Project

1. **Create `ringline.validation.enum` namespace**
   - Implement `case-sensitive-enum` function
   - Implement `validate-enum` helper function
   - Add comprehensive tests

2. **Integrate with mutation executor**
   - Update `build-validation-error` to extract custom error messages
   - Use `malli.error/humanize` for user-friendly errors

3. **Update schema definitions**
   - Replace `:enum` with `case-sensitive-enum` where helpful errors are needed
   - Keep standard `:enum` for internal/system enums

4. **Add documentation**
   - Document enum validation approach
   - Provide examples in schema documentation

---

## Code Examples

### Basic Usage

```clojure
(require '[malli.core :as m])
(require '[malli.error :as me])

;; Define schema
(def status-schema (case-sensitive-enum :active :inactive :pending))

;; Validate
(m/validate status-schema :active)  ;; => true
(m/validate status-schema :ACTIVE)  ;; => false

;; Get helpful error
(-> status-schema
    (m/explain :ACTIVE)
    (me/humanize))
;; => ["Invalid value :ACTIVE. Did you mean :active?"]
```

### In Map Schema

```clojure
(def user-schema
  [:map
   [:id :uuid]
   [:name :string]
   [:status (case-sensitive-enum :active :inactive :pending)]])

(-> user-schema
    (m/explain {:id #uuid "..." :name "Alice" :status :ACTIVE})
    (me/humanize))
;; => {:status ["Invalid value :ACTIVE. Did you mean :active?"]}
```

### Direct Validation

```clojure
(validate-enum :ACTIVE [:active :inactive :pending])
;; => {:valid? false
;;     :value :ACTIVE
;;     :error "Invalid value :ACTIVE. Did you mean :active?"
;;     :suggestion :active}
```

---

## Key Takeaways

1. **Use case-insensitive matching** - Simpler and more effective than Levenshtein distance for enums
2. **Provide helpful suggestions** - "Did you mean X?" significantly improves UX
3. **Leverage Malli's :fn schema** - Best integration with existing validation infrastructure
4. **Cache for performance** - Pre-compute lowercase versions for O(1) lookup
5. **Keep it simple** - Don't over-engineer with complex similarity algorithms

---

## Files Created

1. **specs/enum-validation-research.md** - Comprehensive research document with detailed explanations
2. **specs/enum-validation-example.clj** - Practical code examples ready to use
3. **specs/enum-validation-summary.md** - This executive summary

---

## Next Steps

1. Review the research findings and code examples
2. Decide on implementation approach for Ringline
3. Create `ringline.validation.enum` namespace
4. Add tests for enum validation
5. Integrate with mutation executor
6. Update documentation

---

## References

- **Malli Documentation**: https://github.com/metosin/malli
- **clj-fuzzy Library**: https://github.com/Yomguithereal/clj-fuzzy
- **Clojure String Functions**: https://clojuredocs.org/clojure.string
- **Error Message Best Practices**: Industry standard UX guidelines

