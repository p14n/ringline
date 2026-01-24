(ns ringline.mutation.parser
  "Parse mutation definitions from Malli schemas.
   
   This namespace extracts mutation metadata from entity schemas and derives
   input schemas for create, update, and delete operations."
  (:require [malli.core :as m]
            [ringline.schema.properties :as props]))

;; T013: Implement get-mutation-property
(defn get-mutation-property
  "Extract :ringline/mutations property from schema, returns empty set if not present.
   
   Args:
     schema - A Malli schema (vector starting with :map)
   
   Returns:
     Set of allowed mutation operations (#{:create :update :delete} or subset)"
  [schema]
  (let [properties (m/properties schema)]
    (props/get-mutations properties)))

;; T014: Implement derive-input-schema
(defn derive-input-schema
  "Derive input schema for a specific mutation operation from entity schema.
   
   - Create: all required fields except :id
   - Update: all fields optional except :id
   - Delete: only :id field
   
   Args:
     entity-schema - The entity's Malli schema
     operation - The mutation operation (:create, :update, or :delete)
   
   Returns:
     Malli schema for the specified operation's input"
  [entity-schema operation]
  (when-not (#{:create :update :delete} operation)
    (throw (ex-info "Invalid operation type" {:operation operation})))
  
  (let [children (m/children entity-schema)
        fields (filter vector? children)]  ; Filter out property maps
    
    (case operation
      :create
      ;; Create: all fields except :id (required by default)
      (into [:map]
            (filter #(not= :id (first %)) fields))
      
      :update
      ;; Update: :id is required, all other fields are optional
      (into [:map]
            (map (fn [field]
                   (let [[field-name second-elem third-elem] field
                         ;; Malli children have format: [name props schema] where props can be nil
                         ;; If second-elem is a map, it's props and third-elem is schema
                         ;; If second-elem is nil, third-elem is schema
                         ;; Otherwise, second-elem is schema
                         props (if (map? second-elem) second-elem {})
                         schema (cond
                                  (map? second-elem) third-elem
                                  (nil? second-elem) third-elem
                                  :else second-elem)
                         ;; ID is required, all other fields are optional
                         final-props (if (= field-name :id)
                                       props
                                       (assoc props :optional true))]
                     [field-name final-props schema]))
                 fields))
      
      :delete
      ;; Delete: only :id field
      [:map
       (first (filter #(= :id (first %)) fields))])))

;; T015: Implement parse-mutations
(defn parse-mutations
  "Extract mutation definitions from entity schema properties.
   Returns a map of mutation definitions keyed by operation type.
   
   Args:
     entity-type - The entity type keyword (e.g., :user, :post)
     schema - The Malli schema for the entity
   
   Returns:
     Map containing:
       :entity-type - The entity type
       :operations - Set of allowed operations
       :create-schema - Malli schema for create input (if :create in operations)
       :update-schema - Malli schema for update input (if :update in operations)
       :delete-schema - Malli schema for delete input (if :delete in operations)"
  [entity-type schema]
  (when-not (vector? schema)
    (throw (ex-info "Invalid schema format: must be a vector" {:schema schema})))
  
  (let [operations (get-mutation-property schema)
        result {:entity-type entity-type
                :operations operations}]
    (cond-> result
      (contains? operations :create)
      (assoc :create-schema (derive-input-schema schema :create))
      
      (contains? operations :update)
      (assoc :update-schema (derive-input-schema schema :update))
      
      (contains? operations :delete)
      (assoc :delete-schema (derive-input-schema schema :delete)))))

;; Rich comment block with REPL examples
(comment
  ;; Example: Parse mutations from a schema
  (require '[ringline.mutation.parser :as parser])
  (require '[malli.core :as m])

  ;; Define a schema with mutations
  (def user-schema
    [:map
     {:ringline/datomic-ns :user
      :ringline/mutations #{:create :update :delete}}
     [:id :uuid]
     [:username :string]
     [:email :string]
     [:created-at :int]])

  ;; Parse mutation definitions
  (parser/parse-mutations :user user-schema)
  ;; => {:entity-type :user
  ;;     :operations #{:create :update :delete}
  ;;     :create-schema [:map [:username :string] [:email :string] [:created-at :int]]
  ;;     :update-schema [:map [:username {:optional true} :string] ...]
  ;;     :delete-schema [:map [:id :uuid]]}

  ;; Get mutation property
  (parser/get-mutation-property user-schema)
  ;; => #{:create :update :delete}

  ;; Derive input schemas
  (parser/derive-input-schema user-schema :create)
  ;; => [:map [:username :string] [:email :string] [:created-at :int]]

  (parser/derive-input-schema user-schema :update)
  ;; => [:map [:username {:optional true} :string] ...]

  (parser/derive-input-schema user-schema :delete)
  ;; => [:map [:id :uuid]]

  ;; Schema with subset of mutations
  (def post-schema
    [:map
     {:ringline/datomic-ns :post
      :ringline/mutations #{:create :update}}
     [:id :uuid]
     [:title :string]
     [:content :string]])

  (parser/parse-mutations :post post-schema)
  ;; => {:entity-type :post
  ;;     :operations #{:create :update}
  ;;     :create-schema [:map [:title :string] [:content :string]]
  ;;     :update-schema [:map [:title {:optional true} :string] ...]
  ;;     ;; No :delete-schema
  ;;     }

  )

