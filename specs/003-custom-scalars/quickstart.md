# Quickstart: Using Custom Scalars in Ringline

**Feature**: Custom Scalars Support  
**Branch**: `003-custom-scalars`  
**Date**: 2026-01-24

This guide shows you how to use the four new custom scalar types (Date, DateTime, Enum, Decimal) in your Ringline applications.

---

## Prerequisites

- Ringline framework with custom scalars support
- Clojure 1.12.0+
- Basic understanding of Malli schemas

---

## 1. Date Fields (Calendar Dates)

### Define a Schema with Date Fields

```clojure
(require '[ringline.core :as ringline])

(def Event
  [:map
   {:ringline/datomic-ns "event"
    :ringline/query-root true
    :ringline/searchable [:event-date]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:event-date :time/local-date]           ; Date scalar
   [:deadline {:optional true} :time/local-date]])
```

### Create an Event with a Date

```graphql
mutation {
  createEvent(
    name: "Product Launch"
    eventDate: "2024-06-15"
    deadline: "2024-06-01"
  ) {
    id
    name
    eventDate
    deadline
  }
}
```

### Query Events by Date

```graphql
query {
  events(eventDate: "2024-06-15") {
    id
    name
    eventDate
    deadline
  }
}
```

### Key Points

- **Format**: ISO8601 date string (10 characters: `YYYY-MM-DD`)
- **Valid examples**: `"2024-01-15"`, `"2000-12-31"`
- **Invalid examples**: `"01/15/2024"`, `"2024-1-15"`, `"2024-02-30"`
- **Storage**: Stored as Datomic `:db.type/instant` at midnight UTC
- **No year restrictions**: Any valid ISO8601 date is accepted

---

## 2. DateTime Fields (Timestamps with Timezone)

### Define a Schema with DateTime Fields

```clojure
(def Task
  [:map
   {:ringline/datomic-ns "task"
    :ringline/query-root true
    :ringline/searchable [:status :priority]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:title :string]
   [:status [:enum :draft :in-progress :completed :cancelled]]
   [:priority [:enum :low :medium :high :urgent]]
   [:created-at :time/offset-date-time]     ; DateTime scalar
   [:updated-at :time/offset-date-time]
   [:scheduled-for {:optional true} :time/offset-date-time]
   [:due-date {:optional true} :time/local-date]])
```

### Create a Task with DateTime

```graphql
mutation {
  createTask(
    title: "Review quarterly report"
    status: IN_PROGRESS
    priority: HIGH
    scheduledFor: "2024-01-20T14:30:00-05:00"
    dueDate: "2024-01-25"
  ) {
    id
    title
    scheduledFor
    createdAt
  }
}
```

### Query Tasks by DateTime Range

```graphql
query {
  tasks(
    scheduledAfter: "2024-01-15T00:00:00+00:00"
    scheduledBefore: "2024-01-31T23:59:59+00:00"
  ) {
    id
    title
    scheduledFor
  }
}
```

### Key Points

- **Format**: ISO8601 datetime string with timezone (~25 characters: `YYYY-MM-DDTHH:MM:SS±HH:MM`)
- **Valid examples**: `"2024-01-15T14:30:00+05:00"`, `"2024-01-15T14:30:00Z"`
- **Invalid examples**: `"2024-01-15T14:30:00"` (no timezone - REJECTED)
- **Timezone required**: Values without timezone offset are rejected with validation error
- **Storage**: Stored as Datomic `:db.type/instant` (UTC) + `:db.type/string` (timezone offset)
- **Timezone preserved**: Original timezone offset is maintained

---

## 3. Enum Fields (Constrained Values)

### Define a Schema with Enum Fields

```clojure
(def Task
  [:map
   {:ringline/datomic-ns "task"
    :ringline/query-root true
    :ringline/searchable [:status :priority]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:title :string]
   [:status [:enum :draft :in-progress :completed :cancelled]]  ; Enum
   [:priority [:enum :low :medium :high :urgent]]])             ; Enum
```

### Create a Task with Enum Values

```graphql
mutation {
  createTask(
    title: "Implement custom scalars"
    status: DRAFT
    priority: URGENT
  ) {
    id
    title
    status
    priority
  }
}
```

### Query Tasks by Enum Value

```graphql
query {
  tasks(status: COMPLETED, priority: HIGH) {
    id
    title
    status
    priority
  }
}
```

### Key Points

- **Case-sensitive**: Enum values are case-sensitive (e.g., `DRAFT` ≠ `draft`)
- **Error messages**: Invalid values show valid options and suggest correct casing
  - Example: `"Invalid value 'draft'. Valid options: DRAFT, IN_PROGRESS, COMPLETED, CANCELLED. Did you mean 'DRAFT'?"`
- **Storage**: Stored as Datomic `:db.type/keyword`
- **GraphQL**: Automatically generates GraphQL enum types

---

## 4. Decimal Fields (Precise Numeric Values)

### Define a Schema with Decimal Fields

```clojure
(def Product
  [:map
   {:ringline/datomic-ns "product"
    :ringline/query-root true
    :ringline/searchable [:category]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:price [:decimal {:precision 38 :scale 10}]]              ; Decimal scalar
   [:weight-kg {:optional true} [:decimal {:precision 38 :scale 10}]]
   [:category [:enum :electronics :clothing :food :books]]])
```

### Create a Product with Decimal Price

```graphql
mutation {
  createProduct(
    name: "Premium Coffee Beans"
    price: "19.99"
    weightKg: "0.5000000000"
    category: FOOD
  ) {
    id
    name
    price
    weightKg
  }
}
```

### Query Products by Price Range

```graphql
query {
  products(
    minPrice: "10.00"
    maxPrice: "100.00"
  ) {
    id
    name
    price
    category
  }
}
```

### Key Points

- **Precision limit**: Maximum 38 total digits
- **Scale limit**: Maximum 10 digits after decimal point
- **No floating-point errors**: Uses `BigDecimal` for exact arithmetic
- **Format**: Accept numbers or strings, return strings (avoid JavaScript precision loss)
- **Valid examples**: `"19.99"`, `"1234567890.1234567890"`
- **Invalid examples**: `"12345678901234567890123456789012345678.9"` (39 digits), `"1.12345678901"` (11 decimal places)
- **Storage**: Stored as Datomic `:db.type/bigdec`
- **Use cases**: Currency, measurements, percentages

---

## 5. Complete Example

### Define Schemas

```clojure
(ns myapp.schemas
  (:require [ringline.core :as ringline]))

(def Event
  [:map
   {:ringline/datomic-ns "event"
    :ringline/query-root true
    :ringline/searchable [:event-date]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:event-date :time/local-date]
   [:deadline {:optional true} :time/local-date]])

(def Task
  [:map
   {:ringline/datomic-ns "task"
    :ringline/query-root true
    :ringline/searchable [:status :priority]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:title :string]
   [:status [:enum :draft :in-progress :completed :cancelled]]
   [:priority [:enum :low :medium :high :urgent]]
   [:created-at :time/offset-date-time]
   [:updated-at :time/offset-date-time]
   [:scheduled-for {:optional true} :time/offset-date-time]
   [:due-date {:optional true} :time/local-date]])

(def Product
  [:map
   {:ringline/datomic-ns "product"
    :ringline/query-root true
    :ringline/searchable [:category]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:price [:decimal {:precision 38 :scale 10}]]
   [:weight-kg {:optional true} [:decimal {:precision 38 :scale 10}]]
   [:category [:enum :electronics :clothing :food :books]]
   [:launch-date {:optional true} :time/local-date]])

;; Initialize framework
(def framework
  (ringline/init-framework
    {:Event Event
     :Task Task
     :Product Product}
    {}))
```

### Run Queries

See `contracts/example-queries.graphql` for complete query and mutation examples.

---

## Error Handling

See `contracts/error-examples.md` for detailed error message examples for each scalar type.

---

## Next Steps

- Read the full specification: `spec.md`
- Review the data model: `data-model.md`
- Explore GraphQL contracts: `contracts/`
- Run integration tests: `test/ringline/integration/custom_scalars_integration_test.clj`
