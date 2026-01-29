(ns ringline.fixtures
  "Test fixtures with example Malli schemas"
  (:require [malli.core :as m]))

;; Example User entity schema
(def user-schema
  [:map
   {:ringline/schema-name :user
    :ringline/datomic-ns :user
    :ringline/query-root true
    :ringline/searchable [:email :username]}
   [:id :uuid]
   [:username :string]
   [:email :string]
   [:created-at :int]  ; Using :int for timestamp (epoch milliseconds)
   [:posts {:ringline/ref-to :post} [:vector :uuid]]])  ; One-to-many relationship

;; Example Post entity schema
(def post-schema
  [:map
   {:ringline/schema-name :post
    :ringline/datomic-ns :post
    :ringline/query-root true
    :ringline/searchable [:title]}
   [:id :uuid]
   [:title :string]
   [:content :string]
   [:published? :boolean]
   [:created-at :int]  ; Using :int for timestamp (epoch milliseconds)
   [:author {:ringline/ref-to :user} :uuid]  ; Many-to-one relationship
   [:tags [:vector :string]]])

;; Example Comment entity schema (for nested relationship testing)
(def comment-schema
  [:map
   {:ringline/schema-name :comment
    :ringline/datomic-ns :comment}
   [:id :uuid]
   [:text :string]
   [:created-at :int]  ; Using :int for timestamp (epoch milliseconds)
   [:post {:ringline/ref-to :post} :uuid]  ; Many-to-one relationship
   [:author {:ringline/ref-to :user} :uuid]])  ; Many-to-one relationship

;; Multi-entity schema map
(def test-schemas
  [user-schema
   post-schema
   comment-schema])

;; Simple schema without custom properties (for basic testing)
(def simple-schema
  [:map
   [:id :uuid]
   [:name :string]
   [:age :int]])

;; Schema with enum type
(def status-schema
  [:map
   {:ringline/datomic-ns :task}
   [:id :uuid]
   [:status [:enum :pending :in-progress :completed :cancelled]]
   [:title :string]])

;; Schema with nested maps
(def profile-schema
  [:map
   {:ringline/datomic-ns :profile}
   [:id :uuid]
   [:user :uuid]  ; Reference (using :uuid instead of :ref)
   [:settings [:map
               [:theme [:enum :light :dark]]
               [:notifications :boolean]
               [:language :string]]]])

;; ========================================
;; Mutation Test Fixtures
;; ========================================

;; Schema with all mutation operations
(def user-with-mutations-schema
  [:map
   {:ringline/schema-name :user
    :ringline/datomic-ns :user
    :ringline/query-root true
    :ringline/searchable [:email :username]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:username :string]
   [:email :string]
   [:created-at :int]])

;; Schema with subset of mutations (create and update only)
(def post-with-partial-mutations-schema
  [:map
   {:ringline/schema-name :post
    :ringline/datomic-ns :post
    :ringline/query-root true
    :ringline/mutations #{:create :update}}
   [:id :uuid]
   [:title :string]
   [:content :string]
   [:published? :boolean]
   [:created-at :int]])

;; Schema with no mutations property (read-only entity)
(def readonly-schema
  [:map
   {:ringline/datomic-ns :audit-log
    :ringline/query-root true}
   [:id :uuid]
   [:event :string]
   [:timestamp :int]])

;; Schema with only delete mutation
(def deletable-only-schema
  [:map
   {:ringline/schema-name :user
    :ringline/datomic-ns :temp-data
    :ringline/mutations #{:delete}}
   [:id :uuid]
   [:data :string]])

