(ns ringline.query.converter
  "Convert GraphQL queries to Datomic pull patterns"
  (:require [malli.core :as m]
            [clojure.string :as str]))

;; Malli schemas for validation

(def QueryContext
  "Schema for query context extracted from Lacinia"
  [:map
   [:entity-type :keyword]
   [:selections :vector]
   [:arguments :map]
   [:nested-queries :map]])

(def PullPattern
  "Schema for Datomic pull pattern"
  [:map
   [:pattern :vector]
   [:entity-id {:optional true} :any]
   [:where-clauses {:optional true} :vector]])

;; Helper functions

(defn- keyword->namespaced
  "Convert a simple keyword to a namespaced keyword using entity type"
  [entity-type field-kw]
  (keyword (str/lower-case (name entity-type)) (name field-kw)))

(defn- extract-selections
  "Extract field selections from Lacinia selection map"
  [selection-map]
  (when selection-map
    (vec (keys (:selections selection-map)))))

(defn- extract-nested-selections
  "Extract nested selections (relationships) from Lacinia selection map"
  [selection-map]
  (when-let [selections (:selections selection-map)]
    (into {}
          (keep (fn [[field-name field-data]]
                  (when (contains? field-data :selections)
                    [field-name {:selections (vec (keys (:selections field-data)))
                                 :nested-queries (extract-nested-selections field-data)}]))
                selections))))

;; Query context building

(defn build-query-context
  "Build QueryContext from Lacinia resolver context.
   
   Args:
     lacinia-context - Lacinia resolver context map
     entity-type - Keyword representing the entity type (e.g., :User)
   
   Returns:
     QueryContext map with :entity-type, :selections, :arguments, :nested-queries"
  [lacinia-context entity-type]
  (let [selection (get lacinia-context :com.walmartlabs.lacinia/selection)
        selections (extract-selections selection)
        arguments (or (:arguments selection) {})
        nested-queries (or (extract-nested-selections selection) {})]
    {:entity-type entity-type
     :selections selections
     :arguments arguments
     :nested-queries nested-queries}))

;; Pull pattern generation

(defn- generate-nested-pull
  "Generate nested pull pattern for a relationship field"
  [entity-type field-name nested-query]
  (let [nested-selections (:selections nested-query)
        nested-nested (:nested-queries nested-query)
        ;; Assume relationship field name matches target entity (e.g., :posts -> :post)
        target-entity (keyword (str/replace (name field-name) #"s$" ""))
        namespaced-fields (mapv #(keyword->namespaced target-entity %) nested-selections)
        ;; Recursively handle deeper nesting
        nested-pulls (when (seq nested-nested)
                       (mapv (fn [[nested-field nested-data]]
                               (generate-nested-pull target-entity nested-field nested-data))
                             nested-nested))]
    {(keyword->namespaced entity-type field-name)
     (vec (concat namespaced-fields nested-pulls))}))

(defn- build-pull-pattern
  "Build Datomic pull pattern from query context"
  [query-ctx]
  (let [entity-type (:entity-type query-ctx)
        selections (:selections query-ctx)
        nested-queries (:nested-queries query-ctx)
        ;; Convert simple selections to namespaced keywords
        simple-fields (remove #(contains? nested-queries %) selections)
        namespaced-fields (mapv #(keyword->namespaced entity-type %) simple-fields)
        ;; Generate nested pulls for relationships
        nested-pulls (mapv (fn [[field-name nested-query]]
                             (generate-nested-pull entity-type field-name nested-query))
                           nested-queries)]
    (vec (concat namespaced-fields nested-pulls))))

(defn graphql->pull
  "Convert GraphQL field selections to Datomic pull pattern.
   
   Args:
     query-context - QueryContext map
   
   Returns:
     PullPattern map with :pattern vector"
  [query-context]
  (let [pattern (build-pull-pattern query-context)
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
  [query-context]
  (let [pattern (build-pull-pattern query-context)
        where-clauses (build-where-clauses query-context)
        result {:pattern pattern
                :where-clauses where-clauses}]
    ;; Validate the result
    (when-not (m/validate PullPattern result)
      (throw (ex-info "Invalid PullPattern with where clauses"
                      {:query-context query-context
                       :errors (m/explain PullPattern result)})))
    result))

