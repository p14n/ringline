(ns ringline.query.converter
  "Convert GraphQL queries to Datomic pull patterns"
  (:require [malli.core :as m]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.executor :as executor]))

;; Malli schemas for validation
(def PullPattern
  "Schema for Datomic pull pattern"
  [:map
   [:pattern [:vector :any]]
   [:entity-id {:optional true} :any]
   [:where-clauses {:optional true} [:vector :any]]])

;; Helper functions

(defn- keyword->namespaced
  "Convert a simple keyword to a namespaced keyword using entity type"
  [entity-type namespace-lookup field-kw]
  (keyword
   (or (get namespace-lookup entity-type)
       (some-> (if (keyword? entity-type)
                 (name entity-type)
                 entity-type)
               (str/lower-case)))
   (name field-kw)))

(defn- qualified-field->simple
  "Convert a qualified field name (e.g., :User/name) to simple keyword (e.g., :name)"
  [qualified-kw]
  (some-> qualified-kw (name) (keyword)))

(declare extract-nested-from-tree)

(defn dedupe-selections [selections nested]
  (let [ks (set (keys nested))]
    (remove ks selections)))

(defn extract-nested-from-tree-kv [[k v]]
  (cond (= [nil] v) nil
        (map? v) (extract-nested-from-tree v)
        (coll? v) (when-let [maybe-nested (some #(when (map? %) (:selections %)) v)]
                    (let [selected (map qualified-field->simple (keys maybe-nested))
                          extracted (extract-nested-from-tree maybe-nested)
                          s (qualified-field->simple k)]
                      (if (seq extracted)
                        [s {:selections (dedupe-selections selected extracted)
                            :nested-queries extracted}]
                        [s {:selections selected}])))
        :else nil))

(defn extract-nested-from-tree [tree]
  (->> tree
       (map extract-nested-from-tree-kv)
       (remove nil?)
       (into {})))

(defn- extract-selections
  "Extract field selections from Lacinia context (handles both real and test formats)"
  [lacinia-context]
  (let [tree (executor/selections-tree lacinia-context)]
    (if tree
      ;; Convert qualified field names to simple keywords 
      (vec (map qualified-field->simple (keys tree)))
      [])))

(defn- extract-nested-selections
  "Extract nested selections from Lacinia context (handles both real and test formats)"
  [lacinia-context]
  (let [tree (executor/selections-tree lacinia-context)]
    (if tree
      (extract-nested-from-tree tree)
      {})))

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
   (try (let [;; For test compatibility, check if lacinia-context has :com.walmartlabs.lacinia/selection
              ;; or if it's the old test format with :selections directly
              selection (get lacinia-context :com.walmartlabs.lacinia/selection lacinia-context)
              selections (extract-selections lacinia-context)
              ;; Use provided args, or fall back to extracting from selection (for test compatibility)
              arguments (or args (:arguments selection) {})
              nested-queries (or (extract-nested-selections lacinia-context) {})]
          {:entity-type entity-type
           :selections selections
           :arguments arguments
           :nested-queries nested-queries})
        (catch Exception e
          (.printStackTrace e)
          (throw e)))))

;; Pull pattern generation

(defn- generate-nested-pull
  "Generate nested pull pattern for a relationship field"
  [entity-type field-name nested-query namespace-lookup reverse-lookups]
  (let [nested-selections (:selections nested-query)
        nested-nested (:nested-queries nested-query)
        ;; Assume relationship field name matches target entity (e.g., :posts -> :post)
        target-entity (or (get namespace-lookup [entity-type field-name])
                          (keyword ;(str/replace (name field-name) #"s$" "")
                           (name field-name)))
        namespaced-fields (mapv #(keyword->namespaced target-entity namespace-lookup %) nested-selections)
        ;; Recursively handle deeper nesting
        nested-pulls (when (seq nested-nested)
                       (mapv (fn [[nested-field nested-data]]
                               (generate-nested-pull target-entity nested-field nested-data namespace-lookup reverse-lookups))
                             nested-nested))
        reverse-lookup (get reverse-lookups [entity-type field-name])]
    {(or
      (when reverse-lookup [reverse-lookup :as (keyword->namespaced entity-type namespace-lookup field-name)])
      (keyword->namespaced entity-type namespace-lookup field-name))
     (vec (concat namespaced-fields nested-pulls))}))

(defn- build-pull-pattern
  "Build Datomic pull pattern from query context"
  [query-ctx namespace-lookup reverse-lookups]
  (let [entity-type (:entity-type query-ctx)
        selections (:selections query-ctx)
        nested-queries (:nested-queries query-ctx)
        ;; Convert simple selections to namespaced keywords
        simple-fields (remove #(contains? nested-queries %) selections)
        namespaced-fields (mapv #(keyword->namespaced entity-type namespace-lookup %) simple-fields)
        ;; Generate nested pulls for relationships
        nested-pulls (mapv (fn [[field-name nested-query]]
                             (generate-nested-pull entity-type field-name nested-query namespace-lookup reverse-lookups))
                           nested-queries)]
    (vec (concat namespaced-fields nested-pulls))))

(defn graphql->pull
  "Convert GraphQL field selections to Datomic pull pattern.
   
   Args:
     query-context - QueryContext map
   
   Returns:
     PullPattern map with :pattern vector"
  [query-context]
  (let [pattern (build-pull-pattern query-context {} {})
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
  [entity-type namespace-lookup arg-name arg-value]
  (let [attr (keyword->namespaced entity-type namespace-lookup arg-name)]
    ['?e attr arg-value]))

(defn- build-where-clauses
  "Build Datomic where clauses from query arguments"
  [query-ctx namespace-lookup input-converters]
  (let [entity-type (:entity-type query-ctx)
        arguments (:arguments query-ctx)]
    (mapv (fn [[arg-name arg-value]]
            (if-let [converter (input-converters [entity-type arg-name])]
              (argument->where-clause entity-type namespace-lookup arg-name (converter arg-value))
              (argument->where-clause entity-type namespace-lookup arg-name arg-value)))
          arguments)))

(defn pull-with-args
  "Generate pull pattern with datalog where clauses for filtering.
   
   Args:
     query-context - QueryContext map
   
   Returns:
     Map with :pattern (pull pattern vector) and :where-clauses (datalog clauses)"
  [query-context namespace-lookup reverse-lookups input-converters]
  (let [pattern (build-pull-pattern query-context namespace-lookup reverse-lookups)
        where-clauses (build-where-clauses query-context namespace-lookup input-converters)
        result {:pattern pattern
                :where-clauses where-clauses}]
    ;; Validate the result
    (when-not (m/validate PullPattern result)
      (throw (ex-info "Invalid PullPattern with where clauses"
                      {:query-context query-context
                       :errors (m/explain PullPattern result)})))
    result))

