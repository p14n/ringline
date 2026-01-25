# Quickstart: Custom Query and Mutation Schema Support

**Feature**: 004-custom-schema-resolvers  
**Date**: 2026-01-25  
**Purpose**: Quick examples for using custom queries and mutations

## Example 1: Define a Custom Query

### Step 1: Define Malli Schema with Custom Query

```clojure
(require '[ringline.core :as core])

(def user-schema
  [:map {:ringline/datomic-ns "user"
         :ringline/query-root true
         :ringline/custom-query {:name :searchUsers
                                 :args [:map
                                        [:query :string]
                                        [:limit {:optional true} :int]]
                                 :return-type :User
                                 :description "Search users by query string"}}
   [:id :uuid]
   [:email :string]
   [:username :string]
   [:created-at :int]])
```

### Step 2: Implement Custom Resolver

```clojure
(defn search-users-resolver
  "Custom resolver for searchUsers query"
  [context args value]
  (let [db-conn (:db-conn context)
        query-str (:query args)
        limit (or (:limit args) 10)
        ;; Custom search logic using Datomic
        results (d/q '[:find [(pull ?e [*]) ...]
                       :in $ ?query
                       :where
                       [?e :user/username ?username]
                       [(clojure.string/includes? ?username ?query)]]
                     (d/db db-conn)
                     query-str)]
    (take limit results)))
```

### Step 3: Initialize Framework with Custom Resolver

```clojure
(def framework
  (core/init-framework
    {:user user-schema}
    {:custom-resolvers {:searchUsers search-users-resolver}}))

;; Access generated schemas
(def lacinia-schema (:lacinia framework))
```

### Step 4: Execute Custom Query

```clojure
(require '[com.walmartlabs.lacinia :as lacinia])

(def query "{ searchUsers(query: \"alice\", limit: 5) { id username email } }")

(lacinia/execute lacinia-schema query nil {:db-conn db-conn})
;; => {:data {:searchUsers [{:id "..." :username "alice" :email "alice@example.com"} ...]}}
```

## Example 2: Define a Custom Mutation

### Step 1: Define Malli Schema with Custom Mutation

```clojure
(def order-schema
  [:map {:ringline/datomic-ns "order"
         :ringline/query-root true
         :ringline/mutations #{:create :update :delete}
         :ringline/custom-mutation {:name :approveOrder
                                    :args [:map
                                           [:order-id :uuid]
                                           [:approver-notes {:optional true} :string]]
                                    :return-type :Order
                                    :description "Approve an order with optional notes"}}
   [:id :uuid]
   [:status :string]
   [:total :float]
   [:approved-at {:optional true} :int]
   [:approver-notes {:optional true} :string]])
```

### Step 2: Implement Custom Resolver

```clojure
(defn approve-order-resolver
  "Custom resolver for approveOrder mutation"
  [context args value]
  (let [db-conn (:db-conn context)
        order-id (:order-id args)
        notes (:approver-notes args)
        ;; Custom approval logic
        tx-data [{:db/id [:order/id order-id]
                  :order/status "approved"
                  :order/approved-at (System/currentTimeMillis)
                  :order/approver-notes (or notes "")}]
        tx-result @(d/transact db-conn tx-data)
        ;; Fetch updated order
        updated-order (d/pull (d/db db-conn)
                              '[*]
                              [:order/id order-id])]
    updated-order))
```

### Step 3: Initialize Framework with Custom Resolver

```clojure
(def framework
  (core/init-framework
    {:order order-schema}
    {:custom-resolvers {:approveOrder approve-order-resolver}}))
```

### Step 4: Execute Custom Mutation

```clojure
(def mutation "mutation { approveOrder(orderId: \"...\", approverNotes: \"Looks good\") { id status approvedAt } }")

(lacinia/execute (:lacinia framework) mutation nil {:db-conn db-conn})
;; => {:data {:approveOrder {:id "..." :status "approved" :approvedAt 1234567890}}}
```

## Example 3: Mix Auto-Generated and Custom Operations

```clojure
(def user-schema
  [:map {:ringline/datomic-ns "user"
         :ringline/query-root true
         :ringline/mutations #{:create :update :delete}
         :ringline/custom-query {:name :searchUsers
                                 :args [:map [:query :string]]
                                 :return-type :User}}
   [:id :uuid]
   [:username :string]
   [:email :string]])

(def framework
  (core/init-framework
    {:user user-schema}
    {:custom-resolvers {:searchUsers search-users-resolver}}))

;; Now you have:
;; - Auto-generated query: getUser(id: ID)
;; - Custom query: searchUsers(query: String)
;; - Auto-generated mutations: createUser, updateUser, deleteUser

;; Use both in the same GraphQL query:
(def query "{ 
  getUser(id: \"123\") { username }
  searchUsers(query: \"alice\") { username email }
}")
```

## Example 4: Custom Operation Overrides Auto-Generated

```clojure
;; If you define a custom query with the same name as an auto-generated one,
;; the custom operation takes precedence

(def user-schema
  [:map {:ringline/datomic-ns "user"
         :ringline/query-root true
         :ringline/custom-query {:name :getUser  ; Same name as auto-generated
                                 :args [:map [:username :string]]  ; Different args
                                 :return-type :User}}
   [:id :uuid]
   [:username :string]])

(def custom-get-user-resolver
  (fn [context args value]
    ;; Custom logic: find by username instead of id
    (let [db-conn (:db-conn context)
          username (:username args)]
      (d/pull (d/db db-conn) '[*] [:user/username username]))))

(def framework
  (core/init-framework
    {:user user-schema}
    {:custom-resolvers {:getUser custom-get-user-resolver}}))

;; Now getUser uses your custom resolver with username argument
;; instead of the auto-generated id-based query
```

## Error Handling: Missing Resolver

```clojure
;; This will throw an error during initialization (fail fast)
(def user-schema
  [:map {:ringline/custom-query {:name :searchUsers
                                 :args [:map [:query :string]]
                                 :return-type :User}}
   [:id :uuid]
   [:username :string]])

(core/init-framework
  {:user user-schema}
  {})  ; Missing :custom-resolvers

;; Throws: ex-info "Custom operations missing resolvers"
;; {:missing-resolvers #{:searchUsers}
;;  :custom-operations #{:searchUsers}
;;  :provided-resolvers #{}}
```

## Next Steps

- See `data-model.md` for detailed entity definitions
- See `contracts/` for complete API specifications
- See test files for comprehensive examples

