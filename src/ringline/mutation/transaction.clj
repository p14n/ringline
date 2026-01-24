(ns ringline.mutation.transaction
  "Convert GraphQL mutation inputs to Datomic transaction data.
   
   This namespace transforms mutation inputs into Datomic-compatible
   transaction maps with proper tempids, lookup refs, and namespaced attributes."
  (:require [malli.core :as m]
            [ringline.schema.properties :as props]))

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

;; T050: Implement convert-value
(defn convert-value
  "Convert GraphQL value to Datomic value.
   Currently a pass-through, but allows for future type conversions.
   
   Args:
     field-type - The Malli type keyword
     value - The value to convert
   
   Returns:
     Converted value"
  [field-type value]
  ;; For now, most types pass through directly
  ;; Future: handle special conversions (e.g., date strings to inst)
  value)

;; Helper to get Datomic namespace from schema
(defn- get-datomic-ns
  "Extract Datomic namespace from schema properties"
  [schema]
  (let [properties (m/properties schema)]
    (props/get-datomic-ns properties)))

;; T051: Implement build-create-transaction
(defn build-create-transaction
  "Build Datomic transaction map for creating a new entity.
   
   Args:
     entity-type - The entity type keyword
     input-data - Map of field names to values
     schema - The entity's Malli schema
   
   Returns:
     Transaction map with tempid and namespaced attributes"
  [entity-type input-data schema]
  (let [datomic-ns (or (get-datomic-ns schema) entity-type)
        tempid (generate-tempid)
        ;; Generate a new UUID for the entity's :id field
        entity-id (java.util.UUID/randomUUID)
        ;; Convert all input fields to namespaced attributes
        namespaced-data (into {}
                              (map (fn [[k v]]
                                     [(convert-field-name datomic-ns k) v])
                                   input-data))]
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
     schema - The entity's Malli schema
   
   Returns:
     Transaction map with lookup ref and namespaced attributes"
  [entity-type entity-id input-data schema]
  (let [datomic-ns (or (get-datomic-ns schema) entity-type)
        lookup-ref (generate-lookup-ref datomic-ns entity-id)
        ;; Convert all input fields to namespaced attributes
        namespaced-data (into {}
                              (map (fn [[k v]]
                                     [(convert-field-name datomic-ns k) v])
                                   input-data))]
    (assoc namespaced-data :db/id lookup-ref)))

;; T053: Implement build-delete-transaction
(defn build-delete-transaction
  "Build Datomic transaction for deleting an entity.
   
   Args:
     entity-type - The entity type keyword
     entity-id - The entity's UUID
     schema - The entity's Malli schema
   
   Returns:
     Vector [:db/retractEntity lookup-ref]"
  [entity-type entity-id schema]
  (let [datomic-ns (or (get-datomic-ns schema) entity-type)
        lookup-ref (generate-lookup-ref datomic-ns entity-id)]
    [:db/retractEntity lookup-ref]))

;; Main conversion function
(defn mutation-input->transaction
  "Convert mutation input to Datomic transaction data.
   
   Args:
     mutation-input - Map with :operation, :entity-type, :entity-id (optional), :data (optional)
     schema - The entity's Malli schema
   
   Returns:
     Transaction data (map for create/update, vector for delete)"
  [mutation-input schema]
  (let [{:keys [operation entity-type entity-id data]} mutation-input]
    (case operation
      :create
      (do
        (when-not data
          (throw (ex-info "Create operation requires :data" {:input mutation-input})))
        (build-create-transaction entity-type data schema))
      
      :update
      (do
        (when-not entity-id
          (throw (ex-info "Update operation requires :entity-id" {:input mutation-input})))
        (when-not data
          (throw (ex-info "Update operation requires :data" {:input mutation-input})))
        (build-update-transaction entity-type entity-id data schema))
      
      :delete
      (do
        (when-not entity-id
          (throw (ex-info "Delete operation requires :entity-id" {:input mutation-input})))
        (build-delete-transaction entity-type entity-id schema))
      
      (throw (ex-info "Invalid operation type" {:operation operation})))))

;; Rich comment block with REPL examples
(comment
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

