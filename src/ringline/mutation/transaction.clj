(ns ringline.mutation.transaction
  "Convert GraphQL mutation inputs to Datomic transaction data.

   This namespace transforms mutation inputs into Datomic-compatible
   transaction maps with proper tempids, lookup refs, and namespaced attributes."
  (:require [ringline.schema.scalars :as scalars]
            [ringline.schema.parser :as parser]
            [datomic.api :as da]
            [clojure.string :as str])
  (:import [java.time OffsetDateTime]
           [java.util UUID]))

;; T047: Implement generate-tempid
(defn generate-tempid
  "Generate a unique tempid string for new entities.
   
   Returns:
     String tempid in format 'tempid-<uuid>'"
  []
  (str "tempid-" (UUID/randomUUID)))

;; T048: Implement generate-lookup-ref
(defn generate-lookup-ref
  "Generate a Datomic lookup ref for an existing entity.
   
   Args:
     entity-type - The entity type keyword (e.g., :user)
     entity-id - The entity's UUID
   
   Returns:
     Vector lookup ref [:<entity-type>/id <uuid>]"
  [entity-type entity-id]
  [(keyword (name entity-type) "id") entity-id])

;; T049: Implement convert-field-name
(defn convert-field-name
  "Convert field name to namespaced Datomic attribute.
   
   Args:
     entity-type - The entity type keyword (e.g., :user)
     field-name - The field name keyword (e.g., :username)
   
   Returns:
     Namespaced keyword (e.g., :user/username)"
  [entity-type field-name]
  (keyword (name entity-type) (name field-name)))

;; T032, T052, T072, T093: Extend convert-value to handle Date, DateTime, Enum, and Decimal scalars
(defn convert-value
  "Convert GraphQL value to Datomic value.

   Handles type conversions for custom scalars:
   - Date strings (ISO8601) → Instant (midnight UTC)
   - DateTime strings (ISO8601 with timezone) → Instant (UTC)
   - Enum strings → keywords
   - Decimal strings → BigDecimal

   Args:
     field-type - The Malli type keyword or vector (e.g., :string, [:enum ...], [:decimal {...}])
     value - The value to convert

   Returns:
     Converted value"
  [field-type value]
  (cond
    ;; Date scalar: ISO8601 string → LocalDate → java.util.Date
    ;; OR LocalDate object → java.util.Date (if already parsed by Lacinia)
    (= field-type :time/local-date)
    (cond
      (string? value) (-> value scalars/parse-date scalars/store-date)
      (instance? java.time.LocalDate value) (scalars/store-date value)
      :else value)

    ;; T052: DateTime scalar: ISO8601 string with timezone → OffsetDateTime → Instant
    ;; OR OffsetDateTime object → Instant (if already parsed by Lacinia)
    (= field-type :time/offset-date-time)
    (cond
      (string? value) (-> value scalars/parse-datetime scalars/store-datetime)
      (instance? OffsetDateTime value) (scalars/store-datetime value)
      :else value)

    ;; T072: Enum scalar: string → keyword
    ;; Enum types are vectors like [:enum :draft :in_progress :completed]
    ;; Convert hyphens to underscores to match Malli schema conventions
    (and (vector? field-type) (= :enum (first field-type)))
    (if (string? value)
      (keyword (str/replace value "-" "_"))
      value)

    ;; T093: Decimal scalar: string → BigDecimal
    ;; Decimal types are vectors like [:decimal {:precision 38 :scale 10}]
    (and (vector? field-type) (= :decimal (first field-type)))
    (if (string? value)
      (let [props (second field-type)
            precision (get props :precision 38)
            scale (get props :scale 10)]
        (scalars/parse-decimal value {:precision precision :scale scale}))
      value)

    ;; Simple :decimal keyword (no properties)
    (= field-type :decimal)
    (if (string? value)
      (scalars/parse-decimal value {:precision 38 :scale 10})
      value)

    ;; Default: pass through
    :else
    value))

(defn convert-malli-value [malli-field-type v]
  (cond
    (nil? malli-field-type) v
    (parser/is-schema-var malli-field-type) [(keyword (->> malli-field-type (deref) (parser/malli-schema->datomic-ns))
                                                      "id") v]
    :else (convert-value malli-field-type v)))

(defn create-input->tx-converter [datomic-ns malli-entity]
  (fn [[k v]]
    (let [malli-field-type (parser/malli-entity->malli-field-type malli-entity k)
          converted-value (convert-malli-value malli-field-type v)]
      [(convert-field-name datomic-ns k) converted-value])))

;; T051: Implement build-create-transaction
(defn build-create-transaction
  "Build Datomic transaction map for creating a new entity.

   Args:
     entity-type - The entity type keyword
     input-data - Map of field names to values
     parsed-schema - The parsed entity schema (from parser/parse-schema)

   Returns:
     Transaction map with tempid and namespaced attributes"
  [entity-type input-data parsed-schema schema]
  (let [datomic-ns (or (get-in parsed-schema [:properties :ringline/datomic-ns]) entity-type)
        id-type (parser/id-type parsed-schema)
        tempid (generate-tempid)
        ;; Generate a new UUID for the entity's :id field
        entity-id (case id-type
                    :uuid (da/squuid)
                    (str (java.util.UUID/randomUUID)))
        ;; Convert all input fields to namespaced attributes with value conversion
        namespaced-data (->> input-data
                             (map (create-input->tx-converter datomic-ns schema))
                             (into {}))]
    (assoc namespaced-data
           :db/id tempid
           (convert-field-name datomic-ns :id) entity-id)))

;; T052: Implement build-update-transaction
(defn build-update-transaction
  "Build Datomic transaction map for updating an existing entity.

   Args:
     entity-type - The entity type keyword
     entity-id - The entity's UUID
     input-data - Map of field names to values (partial update)
     parsed-schema - The parsed entity schema (from parser/parse-schema)

   Returns:
     Transaction map with lookup ref and namespaced attributes"
  [entity-type entity-id input-data parsed-schema schema]
  (let [datomic-ns (or (get-in parsed-schema [:properties :ringline/datomic-ns]) entity-type)
        lookup-ref (generate-lookup-ref datomic-ns entity-id)
        ;; Filter out :id field (it's already in the lookup ref)
        ;; and convert remaining fields to namespaced attributes with value conversion
        namespaced-data (->> input-data
                             (filter (fn [[k _]] (not= k :id)))
                             (map (create-input->tx-converter datomic-ns schema))
                             (into {}))]
    (assoc namespaced-data :db/id lookup-ref)))

;; T053: Implement build-delete-transaction
(defn build-delete-transaction
  "Build Datomic transaction for deleting an entity.

   Args:
     entity-type - The entity type keyword
     entity-id - The entity's UUID
     parsed-schema - The parsed entity schema (from parser/parse-schema)

   Returns:
     List (:db/retractEntity lookup-ref)"
  [entity-type entity-id parsed-schema]
  (let [datomic-ns (or (get-in parsed-schema [:properties :ringline/datomic-ns]) entity-type)
        lookup-ref (generate-lookup-ref datomic-ns entity-id)]
    (list :db/retractEntity lookup-ref)))

;; Main conversion function
(defn mutation-input->transaction
  "Convert mutation input to Datomic transaction data.

   Args:
     mutation-input - Map with :operation, :entity-type, :entity-id (optional), :data (optional)
     parsed-schema - The parsed entity schema (from parser/parse-schema)

   Returns:
     Transaction data (map for create/update, vector for delete)"
  ([mutation-input parsed-schema]
   (mutation-input->transaction mutation-input parsed-schema nil))
  ([mutation-input parsed-schema schema]
   (let [{:keys [operation entity-type entity-id data]} mutation-input]
     (case operation
       :create
       (do

         (when-not data
           (throw (ex-info "Create operation requires :data" {:input mutation-input})))
         (build-create-transaction entity-type data parsed-schema schema))

       :update
       (do

         (when-not entity-id
           (throw (ex-info "Update operation requires :entity-id" {:input mutation-input})))
         (when-not data
           (throw (ex-info "Update operation requires :data" {:input mutation-input})))
         (build-update-transaction entity-type entity-id data parsed-schema schema))

       :delete
       (do
         (when-not entity-id
           (throw (ex-info "Delete operation requires :entity-id" {:input mutation-input})))
         (build-delete-transaction entity-type entity-id parsed-schema))

       (throw (ex-info "Invalid operation type" {:operation operation}))))))