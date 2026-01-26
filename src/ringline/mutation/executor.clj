(ns ringline.mutation.executor
  "Execute mutations with validation and error handling.

   This namespace orchestrates the complete mutation execution flow:
   validation, transaction building, execution, and result formatting."
  (:require [malli.core :as m]
            [ringline.mutation.parser :as parser]
            [ringline.mutation.transaction :as tx]
            [datomic.api]
            [spyscope.core]))

;; T065: Implement check-operation-allowed
(defn check-operation-allowed
  "Check if an operation is allowed for the given schema.

   Args:
     operation - The mutation operation (:create, :update, or :delete)
     schema - The entity's Malli schema

   Returns:
     Boolean indicating if operation is allowed"
  [operation schema]
  (let [allowed-ops (parser/get-mutation-property schema)]
    (contains? allowed-ops operation)))

;; T066: Implement build-validation-error
(defn build-validation-error
  "Build a validation error map.

   Args:
     message - Error message string
     field - Optional field name keyword
     value - Optional field value

   Returns:
     Error map with :code, :message, :field, :value"
  ([message]
   {:code :VALIDATION_ERROR
    :message message})
  ([message field value]
   {:code :VALIDATION_ERROR
    :message message
    :field field
    :value value}))

;; T067: Implement validate-mutation-input
(defn validate-mutation-input
  "Validate mutation input against schema and operation constraints.

   Args:
     mutation-input - Map with :operation, :entity-type, :data, :entity-id
     schema - The entity's Malli schema

   Returns:
     Map with :valid? boolean and :errors vector"
  [mutation-input schema]
  (let [{:keys [operation entity-type data]} mutation-input
        errors (atom [])]

    ;; Check if operation is allowed
    (when-not (check-operation-allowed operation schema)
      (swap! errors conj
             (build-validation-error
              (str "Operation " (name operation) " is not allowed for " (name entity-type)))))

    ;; Validate input data against derived schema (for create/update)
    (when (and data (#{:create :update} operation))
      (let [input-schema #spy/d (parser/derive-input-schema schema operation)
            ;; For update operations, merge entity-id into data for validation
            ;; (the input schema expects :id to be present)
            validation-data (if (and (= operation :update) (:entity-id mutation-input))
                              (assoc data :id (:entity-id mutation-input))
                              data)]
        (when-not (m/validate input-schema validation-data)
          (let [explanation (m/explain input-schema validation-data)]
            (swap! errors conj
                   (build-validation-error
                    (str "Invalid input data: " (pr-str explanation))))))))

    {:valid? (empty? @errors)
     :errors @errors}))

;; T068: Implement format-success-result
(defn format-success-result
  "Format a successful mutation result.

   Args:
     operation - The mutation operation
     entity-type - The entity type
     data - The entity data (optional for delete)
     entity-id - The entity UUID

   Returns:
     MutationResult map with success=true"
  [operation entity-type data entity-id]
  {:success true
   :operation operation
   :entity-type entity-type
   :data data
   :entity-id entity-id
   :timestamp (System/currentTimeMillis)})

;; T069: Implement format-error-result
(defn format-error-result
  "Format a failed mutation result.

   Args:
     operation - The mutation operation
     entity-type - The entity type
     errors - Vector of error maps

   Returns:
     MutationResult map with success=false"
  [operation entity-type errors]
  {:success false
   :operation operation
   :entity-type entity-type
   :errors errors
   :timestamp (System/currentTimeMillis)})

;; T070: Implement execute-transaction (mock for now)
(defn- execute-transaction
  "Execute a Datomic transaction.

   Supports both real Datomic connections and mock connections with custom transact functions.

   Args:
     conn - Datomic connection or mock connection with :transact-fn
     tx-data - Transaction data (map or vector of maps)

   Returns:
     Transaction result with :db-after, :tx-data, :tempids"
  [conn tx-data]
  ;; Check if connection has a custom transact function (for testing/mocking)
  (if-let [transact-fn (:transact-fn conn)]
    ;; Use custom transact function (for mocking/testing)
    (transact-fn (if (vector? tx-data) tx-data [tx-data]))
    ;; Use real Datomic transact
    (try
      (let [tx-data-vec (if (vector? tx-data) tx-data [tx-data])
            result @(datomic.api/transact conn tx-data-vec)]
        result)
      (catch Exception e
        (throw (ex-info "Datomic transaction failed"
                        {:tx-data tx-data
                         :error (.getMessage e)}
                        e))))))

;; Helper function to preprocess enum values
(defn- preprocess-enum-values
  "Convert enum values to match Malli schema conventions.
   GraphQL/Lacinia uses hyphens in enum keywords, but Malli schemas use underscores."
  [data parsed-schema]
  (reduce (fn [acc [field-name field-value]]
            (let [field-def (first (filter #(= field-name (:name %)) (:fields parsed-schema)))
                  is-enum? (and field-def (seq (:enum-values field-def)))]
              (if is-enum?
                ;; Convert enum value: replace hyphens with underscores
                (let [converted-value (cond
                                        (string? field-value)
                                        (keyword (clojure.string/replace field-value "-" "_"))

                                        (keyword? field-value)
                                        (keyword (clojure.string/replace (name field-value) "-" "_"))

                                        :else field-value)]
                  (assoc acc field-name converted-value))
                ;; Keep value as-is
                (assoc acc field-name field-value))))
          {}
          data))

;; T071: Implement execute-mutation
(defn execute-mutation
  "Execute a mutation with full validation and error handling.

   Args:
     mutation-input - Map with :operation, :entity-type, :data, :entity-id
     schema - The entity's Malli schema
     parsed-schema - The parsed entity schema (from parser/parse-schema)
     db-conn - Datomic connection

   Returns:
     MutationResult map"
  [mutation-input schema parsed-schema db-conn]
  (let [{:keys [operation entity-type entity-id data]} mutation-input

        ;; Preprocess enum values: convert strings to keywords for Malli validation
        preprocessed-data (when data (preprocess-enum-values data parsed-schema))
        preprocessed-input (assoc mutation-input :data preprocessed-data)

        ;; Step 1: Validate input
        validation (validate-mutation-input preprocessed-input schema)]

    (if-not (:valid? validation)
      ;; Return validation errors
      (format-error-result operation entity-type (:errors validation))

      ;; Step 2: Build transaction (use preprocessed input)
      (try
        (let [tx-data (tx/mutation-input->transaction preprocessed-input parsed-schema)
              _ (println ">>>>" tx-data)

              ;; Step 3: Execute transaction
              tx-result (execute-transaction db-conn tx-data)

              _ (println "<<<<<" tx-result)
              ;; Step 4: Format success result
              result-entity-id (or entity-id
                                   (when (= operation :create)
                                     ;; Extract generated UUID from transaction
                                     (get-in tx-data [(keyword (name entity-type) "id")])))
              result-data (when (#{:create :update} operation) data)]

          (format-success-result operation entity-type result-data result-entity-id))

        (catch Exception e
          ;; Handle transaction errors
          (let [cause-msg (if-let [cause (.getCause e)]
                            (.getMessage cause)
                            (.getMessage e))]
            (format-error-result
             operation
             entity-type
             [{:code :TRANSACTION_FAILED
               :message (str "Datomic transaction failed: " cause-msg)}])))))))

;; Rich comment block with REPL examples
(comment
  ;; Example: Execute mutations
  (require '[ringline.mutation.executor :as executor])

  ;; Define a schema
  (def user-schema
    [:map
     {:ringline/datomic-ns :user
      :ringline/mutations #{:create :update :delete}}
     [:id :uuid]
     [:username :string]
     [:email :string]
     [:created-at :int]])

  ;; Mock DB connection
  (def mock-conn {:db-after {}})

  ;; Execute create mutation
  (def create-input
    {:operation :create
     :entity-type :user
     :data {:username "alice"
            :email "alice@example.com"
            :created-at 1234567890}})

  (executor/execute-mutation create-input user-schema mock-conn)
  ;; => {:success true
  ;;     :operation :create
  ;;     :entity-type :user
  ;;     :data {:username "alice" :email "alice@example.com" :created-at 1234567890}
  ;;     :entity-id #uuid "..."
  ;;     :timestamp 1234567890123}

  ;; Execute update mutation
  (def update-input
    {:operation :update
     :entity-type :user
     :entity-id #uuid "123e4567-e89b-12d3-a456-426614174000"
     :data {:email "newemail@example.com"}})

  (executor/execute-mutation update-input user-schema mock-conn)
  ;; => {:success true
  ;;     :operation :update
  ;;     :entity-type :user
  ;;     :data {:email "newemail@example.com"}
  ;;     :entity-id #uuid "123e4567-e89b-12d3-a456-426614174000"
  ;;     :timestamp 1234567890123}

  ;; Execute delete mutation
  (def delete-input
    {:operation :delete
     :entity-type :user
     :entity-id #uuid "123e4567-e89b-12d3-a456-426614174000"})

  (executor/execute-mutation delete-input user-schema mock-conn)
  ;; => {:success true
  ;;     :operation :delete
  ;;     :entity-type :user
  ;;     :data nil
  ;;     :entity-id #uuid "123e4567-e89b-12d3-a456-426614174000"
  ;;     :timestamp 1234567890123}

  ;; Validation error example
  (def invalid-input
    {:operation :create
     :entity-type :user
     :data {:username 123  ; Invalid: should be string
            :email "alice@example.com"}})

  (executor/execute-mutation invalid-input user-schema mock-conn)
  ;; => {:success false
  ;;     :operation :create
  ;;     :entity-type :user
  ;;     :errors [{:code :VALIDATION_ERROR :message "..."}]
  ;;     :timestamp 1234567890123}

  ;; Helper functions
  (executor/check-operation-allowed :create user-schema)
  ;; => true

  (executor/validate-mutation-input create-input user-schema)
  ;; => {:valid? true :errors []}

  (executor/build-validation-error "Invalid field" :username 123)
  ;; => {:code :VALIDATION_ERROR :message "Invalid field" :field :username :value 123}
  )

