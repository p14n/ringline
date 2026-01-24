(ns ringline.fixtures
  "Test fixtures with example Malli schemas"
  (:require [malli.core :as m]))

;; Example User entity schema
(def user-schema
  [:map
   {:ringline/datomic-ns :user
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
   {:ringline/datomic-ns :post
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
   {:ringline/datomic-ns :comment}
   [:id :uuid]
   [:text :string]
   [:created-at :int]  ; Using :int for timestamp (epoch milliseconds)
   [:post {:ringline/ref-to :post} :uuid]  ; Many-to-one relationship
   [:author {:ringline/ref-to :user} :uuid]])  ; Many-to-one relationship

;; Multi-entity schema map
(def test-schemas
  {:user user-schema
   :post post-schema
   :comment comment-schema})

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

