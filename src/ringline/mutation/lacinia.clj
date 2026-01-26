(ns ringline.mutation.lacinia
  "Generate Lacinia GraphQL mutation schemas from parsed mutation definitions.
   
   This namespace converts parsed mutation definitions into Lacinia-compatible
   mutation field definitions and input object types."
  (:require [malli.core :as m]
            [ringline.schema.types :as types]
            [clojure.string :as str]))

;; Naming conventions

(defn- keyword->pascal-case
  "Convert keyword to PascalCase string"
  [kw]
  (let [parts (str/split (name kw) #"-")]
    (str/join "" (map str/capitalize parts))))

(defn- keyword->camel-case
  "Convert keyword to camelCase string"
  [kw]
  (let [parts (str/split (name kw) #"-")
        first-part (first parts)
        rest-parts (rest parts)]
    (str first-part (str/join "" (map str/capitalize rest-parts)))))

;; T029: Implement mutation-name
(defn mutation-name
  "Generate GraphQL mutation name from entity type and operation.
   Returns camelCase keyword (e.g., :createUser, :updateBlogPost)
   
   Args:
     entity-type - The entity type keyword (e.g., :user, :blog-post)
     operation - The mutation operation (:create, :update, or :delete)
   
   Returns:
     Keyword with camelCase mutation name"
  [entity-type operation]
  (let [op-str (name operation)
        entity-pascal (keyword->pascal-case entity-type)]
    (keyword (str op-str entity-pascal))))

;; T030: Implement input-type-name
(defn input-type-name
  "Generate GraphQL input type name from entity type and operation.
   Returns PascalCase keyword with 'Input' suffix (e.g., :CreateUserInput)
   
   Args:
     entity-type - The entity type keyword (e.g., :user, :blog-post)
     operation - The mutation operation (:create, :update, or :delete)
   
   Returns:
     Keyword with PascalCase input type name"
  [entity-type operation]
  (let [op-pascal (str/capitalize (name operation))
        entity-pascal (keyword->pascal-case entity-type)]
    (keyword (str op-pascal entity-pascal "Input"))))

;; Type mapping

(defn malli-type->graphql-type
  "Convert Malli type (keyword or vector) to GraphQL (Lacinia) type symbol.

   Args:
     malli-type - Malli type keyword (e.g., :string, :int, :uuid) or vector (e.g., [:vector :keyword])

   Returns:
     GraphQL type symbol (e.g., String, Int, ID) or list (e.g., (list String))"
  [malli-type]
  (cond
    ;; Handle vector types like [:vector :keyword]
    (and (vector? malli-type) (= :vector (first malli-type)))
    (let [element-type (second malli-type)
          base-type (types/malli-type->graphql-type element-type)]
      (list 'list base-type))

    ;; Handle simple keyword types
    (keyword? malli-type)
    (types/malli-type->graphql-type malli-type)

    ;; Default: try to convert as-is
    :else
    (types/malli-type->graphql-type malli-type)))

(defn field-type-from-schema-var
  [schema-var field]
  (->> (deref schema-var)
       (filter vector?)
       (map (juxt first last))
       (into {})
       field))

;; T031: Implement generate-input-object
(defn generate-input-object
  "Generate Lacinia input object type from Malli schema.

   Args:
     input-type-name - The name for the input type (keyword)
     malli-schema - The Malli schema (vector starting with :map)

   Returns:
     Map with :fields key containing field definitions"
  [input-type-name malli-schema]
  (let [children (m/children malli-schema)
        fields (filter vector? children)]
    {:fields
     (into {}
           (map (fn [field]
                  (let [[field-name second-elem third-elem] field
                        ;; Field structure can be:
                        ;; [:name :string] - simple field (from original schema)
                        ;; [:name nil :string] - field without options (from derived schema)
                        ;; [:name {:optional true} :string] - field with options
                        has-options? (and (map? second-elem) (not (nil? second-elem)))
                        options (when has-options? second-elem)
                        ;; If second-elem is nil, use third-elem; otherwise use standard logic
                        field-schema (cond
                                       (nil? second-elem) third-elem
                                       has-options? third-elem
                                       :else second-elem)
                        ;; Extract type keyword from schema (handle both keywords and Malli schema objects)
                        field-type (if (keyword? field-schema)
                                     field-schema
                                     ;; For Malli schema objects, check if it's a schema reference
                                     (let [schema-type (m/type field-schema)]
                                       (if (= schema-type :malli.core/schema)
                                         ;; Dereference schema reference using m/form
                                         (let [form (m/form field-schema)]
                                           (cond
                                             (keyword? form) form
                                             (var? form) (field-type-from-schema-var form :id)
                                             :else schema-type))
                                         schema-type)))
                        optional? (:optional options false)
                        ;; Field names are used as-is (no conversion)
                        gql-field-name field-name
                        gql-type (malli-type->graphql-type field-type)]
                    [gql-field-name
                     {:type (if optional?
                              gql-type
                              (list 'non-null gql-type))}]))
                fields))}))

;; T032: Implement generate-mutation-field
(defn generate-mutation-field
  "Generate a single Lacinia mutation field definition.

   Args:
     parsed-mutations - Parsed mutation definition map
     operation - The mutation operation (:create, :update, or :delete)

   Returns:
     Map with :type, :args, and :description keys"
  [parsed-mutations operation]
  (let [entity-type (:entity-type parsed-mutations)
        entity-pascal (keyword->pascal-case entity-type)
        input-schema (get parsed-mutations (keyword (str (name operation) "-schema")))
        input-type (input-type-name entity-type operation)
        description (str (str/capitalize (name operation)) " a new " entity-pascal)
        ;; Delete mutations return Boolean, others return the entity type
        return-type (if (= operation :delete) :Boolean (keyword entity-pascal))]
    {:type return-type
     :args {:input {:type (list 'non-null input-type)}}
     :description description}))

;; T033: Implement generate-mutation-schemas
(defn generate-mutation-schemas
  "Generate Lacinia mutation schema from parsed mutation definitions.
   Returns a map with :mutations and :input-objects keys.

   Args:
     parsed-mutations - Map containing :entity-type, :operations, and input schemas

   Returns:
     Map with :mutations and :input-objects keys"
  [parsed-mutations]
  (let [entity-type (:entity-type parsed-mutations)
        operations (:operations parsed-mutations)

        ;; Generate mutations
        mutations (into {}
                        (map (fn [operation]
                               [(mutation-name entity-type operation)
                                (generate-mutation-field parsed-mutations operation)])
                             operations))

        ;; Generate input objects
        input-objects (into {}
                            (keep (fn [operation]
                                    (when-let [schema (get parsed-mutations (keyword (str (name operation) "-schema")))]
                                      (let [input-name (input-type-name entity-type operation)]
                                        [input-name (generate-input-object input-name schema)])))
                                  operations))]

    {:mutations mutations
     :input-objects input-objects}))

;; Rich comment block with REPL examples
(comment
  ;; Example: Generate mutation schemas
  (require '[ringline.mutation.lacinia :as lacinia])
  (require '[ringline.mutation.parser :as parser])

  ;; Define a schema with mutations
  (def user-schema
    [:map
     {:ringline/datomic-ns :user
      :ringline/mutations #{:create :update :delete}}
     [:id :uuid]
     [:username :string]
     [:email :string]
     [:created-at :int]])

  ;; Parse mutations
  (def parsed (parser/parse-mutations :user user-schema))

  ;; Generate Lacinia mutation schemas
  (lacinia/generate-mutation-schemas parsed)
  ;; => {:createUser {:type :User
  ;;                  :args {:input {:type (non-null :CreateUserInput)}}
  ;;                  :description "Create a new User"}
  ;;     :updateUser {:type :User
  ;;                  :args {:input {:type (non-null :UpdateUserInput)}}
  ;;                  :description "Update a new User"}
  ;;     :deleteUser {:type :User
  ;;                  :args {:input {:type (non-null :DeleteUserInput)}}
  ;;                  :description "Delete a new User"}}

  ;; Generate mutation name
  (lacinia/mutation-name :user :create)
  ;; => :createUser

  (lacinia/mutation-name :blog-post :update)
  ;; => :updateBlogPost

  ;; Generate input type name
  (lacinia/input-type-name :user :create)
  ;; => :CreateUserInput

  (lacinia/input-type-name :blog-post :delete)
  ;; => :DeleteBlogPostInput

  ;; Generate input object
  (lacinia/generate-input-object
   :CreateUserInput
   [:map
    [:username :string]
    [:email :string]
    [:created-at :int]])
  ;; => {:fields {:username {:type (non-null String)}
  ;;              :email {:type (non-null String)}
  ;;              :created_at {:type (non-null Int)}}}

  ;; Generate mutation field
  (lacinia/generate-mutation-field parsed :create)
  ;; => {:type :User
  ;;     :args {:input {:type (non-null :CreateUserInput)}}
  ;;     :description "Create a new User"}

  ;; Type mapping
  (lacinia/malli-type->graphql-type :string)  ;; => String
  (lacinia/malli-type->graphql-type :int)     ;; => Int
  (lacinia/malli-type->graphql-type :uuid)    ;; => ID
  (lacinia/malli-type->graphql-type :boolean) ;; => Boolean
  )

