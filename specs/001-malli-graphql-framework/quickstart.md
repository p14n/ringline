# Quickstart Guide: Malli-GraphQL Framework

**Date**: 2026-01-23  
**Feature**: 001-malli-graphql-framework

## Overview

This guide shows how to use the Malli-GraphQL Framework to define data models once in Malli and automatically generate both Datomic database schemas and Lacinia GraphQL schemas.

## Step 1: Define Your Data Model with Malli

Define your entities using Malli schemas with custom properties:

```clojure
(require '[malli.core :as m])

;; Define User entity
(def User
  [:map
   {:ringline/datomic-ns "user"
    :ringline/query-root true}
   [:user/id [:uuid {:ringline/unique :identity}]]
   [:user/email [:string {:ringline/searchable true}]]
   [:user/name :string]
   [:user/posts [:vector :ref]]])

;; Define Post entity
(def Post
  [:map
   {:ringline/datomic-ns "post"
    :ringline/query-root true}
   [:post/id [:uuid {:ringline/unique :identity}]]
   [:post/title [:string {:ringline/searchable true}]]
   [:post/content :string]
   [:post/created-at :inst]
   [:post/author :ref]])

;; Define the complete schema map
(def schemas
  {:User User
   :Post Post})
```

## Step 2: Initialize the Framework

Generate both Datomic and Lacinia schemas:

```clojure
(require '[ringline.core :as ringline])

;; Initialize framework with your schemas
(def framework-data
  (ringline/init-framework
    schemas
    {:datomic-ns-prefix "app"
     :graphql-naming :camelCase}))

;; Extract generated schemas
(def datomic-schema (:datomic framework-data))
(def lacinia-schema (:lacinia framework-data))
(def parsed-schemas (:parsed framework-data))
```

## Step 3: Create Datomic Database

Use the generated Datomic schema to create your database:

```clojure
(require '[datomic.api :as d])

;; Create database
(def db-uri "datomic:mem://example")
(d/create-database db-uri)
(def conn (d/connect db-uri))

;; Transact the generated schema
@(d/transact conn datomic-schema)

;; Now you can transact data
@(d/transact conn
  [{:user/id (java.util.UUID/randomUUID)
    :user/email "alice@example.com"
    :user/name "Alice"}])
```

## Step 4: Set Up GraphQL with Lacinia

Create resolvers and compile the Lacinia schema:

```clojure
(require '[com.walmartlabs.lacinia :as lacinia]
         '[ringline.core :as ringline])

;; Create resolvers using framework helpers
(def resolvers
  {:resolve-user
   (ringline/create-resolver :User conn (first parsed-schemas))
   
   :resolve-users
   (ringline/create-resolver :User conn (first parsed-schemas))})

;; Attach resolvers to schema
(def compiled-schema
  (-> lacinia-schema
      (ringline.schema.lacinia/attach-resolvers resolvers)
      lacinia/compile))
```

## Step 5: Execute GraphQL Queries

Now you can execute GraphQL queries:

```clojure
;; Execute a query
(def result
  (lacinia/execute
    compiled-schema
    "{ user(id: \"...\") { id email name posts { id title } } }"
    nil
    nil))

;; Result is automatically converted from Datomic format to GraphQL format
```

## Step 6: REPL-Driven Development

Test your schemas interactively in the REPL:

```clojure
;; Parse a single schema
(require '[ringline.schema.parser :as parser])
(def parsed-user (parser/parse-schema User))

;; Generate Datomic schema
(require '[ringline.schema.datomic :as datomic])
(def user-datomic (datomic/generate-schema parsed-user))

;; Generate Lacinia schema
(require '[ringline.schema.lacinia :as lacinia])
(def user-lacinia (lacinia/generate-schema parsed-user))

;; Test query conversion
(require '[ringline.query.converter :as converter])
(def pull-pattern
  (converter/graphql->pull
    {:entity-type :User
     :selections [:id :email :posts]
     :nested-queries {:posts {:selections [:id :title]}}}))

;; Execute pull query
(d/pull (d/db conn) (:pattern pull-pattern) [:user/email "alice@example.com"])

;; Transform result
(require '[ringline.response.transformer :as transformer])
(def graphql-result
  (transformer/datomic->graphql
    (d/pull (d/db conn) (:pattern pull-pattern) [:user/email "alice@example.com"])
    parsed-user))
```

## Custom Properties Reference

### Schema-Level Properties

- `:ringline/datomic-ns` - Namespace for Datomic attributes (e.g., "user" → :user/id)
- `:ringline/query-root` - Mark entity as GraphQL query root (true/false)

### Field-Level Properties

- `:ringline/unique` - Datomic uniqueness (:identity or :value)
- `:ringline/searchable` - Include field as GraphQL query argument
- `:ringline/index` - Create Datomic index (true/false)

## Next Steps

- Add mutations to your GraphQL schema
- Implement custom resolvers for complex queries
- Add authorization logic to resolvers
- Configure field name transformations (kebab-case ↔ camelCase)
- Add pagination support for list queries

## Testing Your Integration

```clojure
(require '[clojure.test :refer [deftest is testing]])

(deftest test-schema-generation
  (testing "Datomic schema generation"
    (let [result (ringline/init-framework schemas {})]
      (is (seq (:datomic result)))
      (is (every? #(contains? % :db/ident) (:datomic result)))))
  
  (testing "Lacinia schema generation"
    (let [result (ringline/init-framework schemas {})]
      (is (contains? (:lacinia result) :objects))
      (is (contains? (:lacinia result) :queries)))))
```

Run tests with Kaocha:

```bash
clojure -M:test
```

