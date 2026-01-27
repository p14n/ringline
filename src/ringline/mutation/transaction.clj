(ns ringline.mutation.transaction
  "Convert GraphQL mutation inputs to Datomic transaction data.

   This namespace transforms mutation inputs into Datomic-compatible
   transaction maps with proper tempids, lookup refs, and namespaced attributes."
  (:require [malli.core :as m]
            [ringline.schema.properties :as props]
            [ringline.schema.scalars :as scalars]
            [datomic.api :as da]
            [spyscope.core]))

;; T047: Implement generate-tempid
(defn generate-tempid
  "Generate a unique tempid string for new entities.
   
   Returns:
     String tempid in format 'tempid-<uuid>'"
  []
  (str "tempid-" (java.util.UUID/randomUUID)))

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
      (instance? java.time.OffsetDateTime value) (scalars/store-datetime value)
      :else value)

    ;; T072: Enum scalar: string → keyword
    ;; Enum types are vectors like [:enum :draft :in_progress :completed]
    ;; Convert hyphens to underscores to match Malli schema conventions
    (and (vector? field-type) (= :enum (first field-type)))
    (if (string? value)
      (keyword (clojure.string/replace value "-" "_"))
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

;; Helper to get Datomic namespace from schema
(defn- get-datomic-ns
  "Extract Datomic namespace from schema properties"
  [schema]
  (let [properties (m/properties schema)]
    (props/get-datomic-ns properties)))

;; Helper function to get field type from parsed schema
(defn- get-field
  "Get the field from the parsed schema.

   Args:
     parsed-schema - The parsed schema map with :fields vector
     field-name - The field name keyword

   Returns:
     The field"
  [parsed-schema field-name]
  (when-let [field (first (filter #(= field-name (:name %)) (:fields parsed-schema)))]
    field))

(defn- get-field-type
  "Get the type of a field from the parsed schema.

   Args:
     parsed-schema - The parsed schema map with :fields vector
     field-name - The field name keyword

   Returns:
     The field type keyword or vector (e.g., :string, :time/local-date, [:enum ...])"
  [parsed-schema field-name]
  (:type (get-field parsed-schema field-name)))

(defn id-type [schema]
  (->> schema
       :fields
       (filter #(-> % :name (= :id)))
       (first)
       :type))

(defn is-schema-var [t]
  (and (= (m/type t) :malli.core/schema)
       (var? (m/form t))))

(defn field-type-from-schema-var
  [schema-var field]
  (->> (deref schema-var)
       (filter vector?)
       (map (juxt first last))
       (into {})
       field))

(defn correct-field-type [f]
  (println "[][]" f (last f) (type (last f)))
  (if (is-schema-var (last f))
    (-> (drop-last f)
        (vec)
        (conj (field-type-from-schema-var (m/form (last f)) :id)))
    f))

(defn malli-entity->field [malli-entity field-kw]
  (->> malli-entity
       (filter vector?)
       (filter #(-> % first (= field-kw)))
       (first)))

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
        id-type (id-type parsed-schema)
        tempid (generate-tempid)
        ;; Generate a new UUID for the entity's :id field
        entity-id (case id-type
                    :uuid (da/squuid)
                    (str (java.util.UUID/randomUUID)))
        ;; Convert all input fields to namespaced attributes with value conversion
        namespaced-data (into {}
                              (map (fn [[k v]]
                                     (let [field #spy/d (get-field parsed-schema k)
                                           _ (println "******><><><>" schema k)
                                           malli-field #spy/d (malli-entity->field schema k)
                                           field-type #spy/d (last malli-field)
                                           _ #spy/d (is-schema-var field-type)
                                           _ #spy/d (when (is-schema-var field-type) (->> field-type (deref) (filter map?) (first) :ringline/datomic-ns))
                                           converted-value (cond
                                                             (nil? field-type) v
                                                             (is-schema-var field-type) #spy/d [(keyword (->> field-type (deref) (filter map?) (first) :ringline/datomic-ns)
                                                                                                         "id") v]
                                                             :else (convert-value field-type v))]
                                       [(convert-field-name datomic-ns k) converted-value]))
                                   input-data))]
    #spy/d (assoc namespaced-data
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
  [entity-type entity-id input-data parsed-schema]
  (let [datomic-ns (or (get-in parsed-schema [:properties :ringline/datomic-ns]) entity-type)
        lookup-ref (generate-lookup-ref datomic-ns entity-id)
        ;; Filter out :id field (it's already in the lookup ref)
        ;; and convert remaining fields to namespaced attributes with value conversion
        namespaced-data (into {}
                              (comp (filter (fn [[k _]] (not= k :id)))
                                    (map (fn [[k v]]
                                           (let [field-type (get-field-type parsed-schema k)
                                                 converted-value (if field-type
                                                                   (convert-value field-type v)
                                                                   v)]
                                             [(convert-field-name datomic-ns k) converted-value]))))
                              input-data)]
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
         (build-update-transaction entity-type entity-id data parsed-schema))

       :delete
       (do
         (when-not entity-id
           (throw (ex-info "Delete operation requires :entity-id" {:input mutation-input})))
         (build-delete-transaction entity-type entity-id parsed-schema))

       (throw (ex-info "Invalid operation type" {:operation operation}))))))

;; Rich comment block with REPL examples
#_(comment
    ;; Example: Convert mutation inputs to Datomic transactions
    (require '[ringline.mutation.transaction :as tx])

    ;; Define a schema
    (def user-schema
      [:map
       {:ringline/datomic-ns :user
        :ringline/mutations #{:create :update :delete}}
       [:id :uuid]
       [:username :string]
       [:email :string]
       [:created-at :int]])

    ;; Create mutation
    (def create-input
      {:operation :create
       :entity-type :user
       :data {:username "alice"
              :email "alice@example.com"
              :created-at 1234567890}})

    (tx/mutation-input->transaction create-input user-schema)
    ;; => {:db/id "tempid-..."
    ;;     :user/id #uuid "..."
    ;;     :user/username "alice"
    ;;     :user/email "alice@example.com"
    ;;     :user/created-at 1234567890}

    ;; Update mutation
    (def update-input
      {:operation :update
       :entity-type :user
       :entity-id #uuid "123e4567-e89b-12d3-a456-426614174000"
       :data {:email "newemail@example.com"}})

    (tx/mutation-input->transaction update-input user-schema)
    ;; => {:db/id [:user/id #uuid "123e4567-e89b-12d3-a456-426614174000"]
    ;;     :user/email "newemail@example.com"}

    ;; Delete mutation
    (def delete-input
      {:operation :delete
       :entity-type :user
       :entity-id #uuid "123e4567-e89b-12d3-a456-426614174000"})

    (tx/mutation-input->transaction delete-input user-schema)
    ;; => [:db/retractEntity [:user/id #uuid "123e4567-e89b-12d3-a456-426614174000"]]

    ;; Helper functions
    (tx/generate-tempid)
    ;; => "tempid-abc123..."

    (tx/generate-lookup-ref :user #uuid "123e4567-e89b-12d3-a456-426614174000")
    ;; => [:user/id #uuid "123e4567-e89b-12d3-a456-426614174000"]

    (tx/convert-field-name :user :username)
    ;; => :user/username

    (tx/convert-value :string "alice")
    ;; => "alice"
    )

