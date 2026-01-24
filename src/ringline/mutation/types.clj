(ns ringline.mutation.types
  "Core Malli schemas for mutation data types.
   
   These schemas define the structure of mutation-related data throughout
   the framework, ensuring type safety and validation at all boundaries.")

;; Mutation Definition
;; Represents a mutation operation (create/update/delete) for an entity

(def MutationDefinition
  "Schema for a mutation definition extracted from an entity schema.
   
   Contains the entity type, allowed operations, and input schemas for each operation."
  [:map
   [:entity-type :keyword]
   [:operations [:set [:enum :create :update :delete]]]
   [:create-schema {:optional true} :any]   ; Malli schema for create input
   [:update-schema {:optional true} :any]   ; Malli schema for update input
   [:delete-schema {:optional true} :any]]) ; Malli schema for delete input

;; Mutation Input
;; The data provided by the client for a mutation operation

(def MutationInput
  "Schema for mutation input data from GraphQL clients.
   
   Contains the operation type, entity type, optional entity ID (for update/delete),
   and the data payload (for create/update)."
  [:map
   [:operation [:enum :create :update :delete]]
   [:entity-type :keyword]
   [:entity-id {:optional true} :uuid]  ; Required for update/delete
   [:data {:optional true} :map]        ; Required for create/update
   [:timestamp {:optional true} :int]]) ; Optional request timestamp

;; Mutation Result
;; The outcome of a mutation operation

(def MutationResult
  "Schema for mutation execution results.
   
   Contains success status, operation type, entity type, optional entity data,
   optional errors, and execution timestamp."
  [:map
   [:success :boolean]
   [:operation [:enum :create :update :delete]]
   [:entity-type :keyword]
   [:data {:optional true} :map]
   [:entity-id {:optional true} :uuid]
   [:errors {:optional true} [:vector :map]]
   [:timestamp :int]])

;; Error Map
;; Structure for mutation errors

(def MutationError
  "Schema for mutation error details.
   
   Contains error code, message, and optional field/value information."
  [:map
   [:code [:enum :VALIDATION_ERROR :CONSTRAINT_VIOLATION :NOT_FOUND :TRANSACTION_FAILED]]
   [:message :string]
   [:field {:optional true} :keyword]
   [:value {:optional true} :any]])

