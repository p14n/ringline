(ns ringline.query.converter
  "Convert GraphQL queries to Datomic pull patterns"
  (:require [malli.core :as m]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.executor :as executor]))

;; Malli schemas for validation

(def QueryContext
  "Schema for query context extracted from Lacinia"
  [:map
   [:entity-type :keyword]
   [:selections [:vector :any]]
   [:arguments :map]
   [:nested-queries :map]])

(def PullPattern
  "Schema for Datomic pull pattern"
  [:map
   [:pattern [:vector :any]]
   [:entity-id {:optional true} :any]
   [:where-clauses {:optional true} [:vector :any]]])

;; Helper functions

(defn- keyword->namespaced
  "Convert a simple keyword to a namespaced keyword using entity type"
  [entity-type field-kw]
  (keyword (some-> (if (keyword? entity-type)
                     (name entity-type)
                     entity-type)
                   (str/lower-case))
           (name field-kw)))

(defn- qualified-field->simple
  "Convert a qualified field name (e.g., :User/name) to simple keyword (e.g., :name)"
  [qualified-kw]
  (some-> qualified-kw (name) (keyword)))

(defn- extract-selections-from-tree
  "Extract field selections from Lacinia selections-tree format.

   The selections-tree returns a map like:
   {:User/name [nil]
    :User/email [nil]
    :User/posts [{:selections {:Post/title [nil]}}]}"
  [selections-tree]
  (when selections-tree
    (vec (keys selections-tree))))

(defn- extract-nested-from-tree
  "Extract nested selections from Lacinia selections-tree format"
  [selections-tree]
  (when selections-tree
    (into {}
          (keep (fn [[field-name field-data-vec]]
                  ;; field-data-vec is a vector of how the field is used
                  ;; Check if any usage has :selections (indicating nested fields)
                  (when-let [nested-data (some #(when (map? %) (:selections %)) field-data-vec)]
                    (let [nested-tree (:selections nested-data)
                          ;; Convert qualified field name to simple keyword
                          simple-field-name (qualified-field->simple field-name)]
                      [simple-field-name {:selections (extract-selections-from-tree nested-data)
                                          :nested-queries (extract-nested-from-tree nested-tree)}])))
                selections-tree))))

(defn- extract-selections-from-map
  "Extract field selections from test-style selection map (for backwards compatibility)"
  [selection-map]
  (when-let [selections (:selections selection-map)]
    (vec (keys selections))))

(defn- extract-nested-selections-from-map
  "Extract nested selections from test-style selection map (for backwards compatibility)"
  [selection-map]
  (when-let [selections (:selections selection-map)]
    (into {}
          (keep (fn [[field-name field-data]]
                  (when (contains? field-data :selections)
                    [field-name {:selections (vec (keys (:selections field-data)))
                                 :nested-queries (extract-nested-selections-from-map field-data)}]))
                selections))))

(defn- extract-selections
  "Extract field selections from Lacinia context (handles both real and test formats)"
  [lacinia-context]
  (cond
    ;; Try to use Lacinia's selections-tree function (real Lacinia context)
    (and (map? lacinia-context) (contains? lacinia-context :com.walmartlabs.lacinia/selection))
    (try
      (let [tree (executor/selections-tree lacinia-context)]
        (if tree
          ;; Convert qualified field names to simple keywords
          (vec (map qualified-field->simple (keys tree)))
          []))
      (catch Exception _
        ;; Fall back to test format
        (extract-selections-from-map (get lacinia-context :com.walmartlabs.lacinia/selection))))

    ;; Test mock format (map with :selections key)
    (and (map? lacinia-context) (contains? lacinia-context :selections))
    (extract-selections-from-map lacinia-context)

    ;; No selections
    :else
    []))

(defn- extract-nested-selections
  "Extract nested selections from Lacinia context (handles both real and test formats)"
  [lacinia-context]
  (cond
    ;; Try to use Lacinia's selections-tree function (real Lacinia context)
    (and (map? lacinia-context) (contains? lacinia-context :com.walmartlabs.lacinia/selection))
    (try
      (let [tree (executor/selections-tree lacinia-context)]
        (if tree
          (extract-nested-from-tree tree)
          {}))
      (catch Exception _
        ;; Fall back to test format
        (extract-nested-selections-from-map (get lacinia-context :com.walmartlabs.lacinia/selection))))

    ;; Test mock format
    (and (map? lacinia-context) (contains? lacinia-context :selections))
    (extract-nested-selections-from-map lacinia-context)

    ;; No nested selections
    :else
    {}))

;; Query context building

(defn build-query-context
  "Build QueryContext from Lacinia resolver context.

   Args:
     lacinia-context - Lacinia resolver context map
     entity-type - Keyword representing the entity type (e.g., :User)
     args - (optional) Arguments map from resolver, if not provided will try to extract from selection

   Returns:
     QueryContext map with :entity-type, :selections, :arguments, :nested-queries"
  ([lacinia-context entity-type]
   (build-query-context lacinia-context entity-type nil))
  ([lacinia-context entity-type args]
   (let [;; For test compatibility, check if lacinia-context has :com.walmartlabs.lacinia/selection
         ;; or if it's the old test format with :selections directly
         selection (get lacinia-context :com.walmartlabs.lacinia/selection lacinia-context)
         selections (extract-selections lacinia-context)
         ;; Use provided args, or fall back to extracting from selection (for test compatibility)
         arguments (or args (:arguments selection) {})
         nested-queries (or (extract-nested-selections lacinia-context) {})]
     {:entity-type entity-type
      :selections selections
      :arguments arguments
      :nested-queries nested-queries})))

;; Pull pattern generation

(defn- generate-nested-pull
  "Generate nested pull pattern for a relationship field"
  [entity-type field-name nested-query namespace-lookup]
  (let [nested-selections (:selections nested-query)
        nested-nested (:nested-queries nested-query)
        ;; Assume relationship field name matches target entity (e.g., :posts -> :post)
        target-entity (or (get namespace-lookup [entity-type field-name])
                          (keyword (str/replace (name field-name) #"s$" "")))
        namespaced-fields (mapv #(keyword->namespaced target-entity %) nested-selections)
        ;; Recursively handle deeper nesting
        nested-pulls (when (seq nested-nested)
                       (mapv (fn [[nested-field nested-data]]
                               (generate-nested-pull target-entity nested-field nested-data namespace-lookup))
                             nested-nested))]
    {(keyword->namespaced entity-type field-name)
     (vec (concat namespaced-fields nested-pulls))}))

(defn- build-pull-pattern
  "Build Datomic pull pattern from query context"
  [query-ctx namespace-lookup]
  (let [entity-type (:entity-type query-ctx)
        selections (:selections query-ctx)
        nested-queries (:nested-queries query-ctx)
        ;; Convert simple selections to namespaced keywords
        simple-fields (remove #(contains? nested-queries %) selections)
        namespaced-fields (mapv #(keyword->namespaced entity-type %) simple-fields)
        ;; Generate nested pulls for relationships
        nested-pulls (mapv (fn [[field-name nested-query]]
                             (generate-nested-pull entity-type field-name nested-query namespace-lookup))
                           nested-queries)]
    (vec (concat namespaced-fields nested-pulls))))

(defn graphql->pull
  "Convert GraphQL field selections to Datomic pull pattern.
   
   Args:
     query-context - QueryContext map
   
   Returns:
     PullPattern map with :pattern vector"
  [query-context]
  (let [pattern (build-pull-pattern query-context {})
        result {:pattern pattern}]
    ;; Validate the result
    (when-not (m/validate PullPattern result)
      (throw (ex-info "Invalid PullPattern"
                      {:query-context query-context
                       :errors (m/explain PullPattern result)})))
    result))

;; Where clause generation

(defn- argument->where-clause
  "Convert a query argument to a Datomic where clause"
  [entity-type arg-name arg-value]
  (let [attr (keyword->namespaced entity-type arg-name)]
    ['?e attr arg-value]))

(defn- build-where-clauses
  "Build Datomic where clauses from query arguments"
  [query-ctx]
  (let [entity-type (:entity-type query-ctx)
        arguments (:arguments query-ctx)]
    (mapv (fn [[arg-name arg-value]]
            (argument->where-clause entity-type arg-name arg-value))
          arguments)))

(defn pull-with-args
  "Generate pull pattern with datalog where clauses for filtering.
   
   Args:
     query-context - QueryContext map
   
   Returns:
     Map with :pattern (pull pattern vector) and :where-clauses (datalog clauses)"
  [query-context namespace-lookup]
  (let [pattern (build-pull-pattern query-context namespace-lookup)
        where-clauses (build-where-clauses query-context)
        result {:pattern pattern
                :where-clauses where-clauses}]
    ;; Validate the result
    (when-not (m/validate PullPattern result)
      (throw (ex-info "Invalid PullPattern with where clauses"
                      {:query-context query-context
                       :errors (m/explain PullPattern result)})))
    result))

