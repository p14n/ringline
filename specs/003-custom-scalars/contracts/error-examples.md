# Error Examples: Custom Scalars Validation

This document demonstrates expected error messages for invalid scalar values.

---

## Date Scalar Errors

### Invalid Format (Wrong Separator)

**Input:**
```graphql
mutation {
  createEvent(
    name: "Test Event"
    eventDate: "01/15/2024"
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid date format. Expected YYYY-MM-DD, got: 01/15/2024",
    "extensions": {
      "expected-format": "YYYY-MM-DD",
      "received": "01/15/2024"
    }
  }]
}
```

### Invalid Date (February 30)

**Input:**
```graphql
mutation {
  createEvent(
    name: "Test Event"
    eventDate: "2024-02-30"
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid date format. Expected YYYY-MM-DD, got: 2024-02-30",
    "extensions": {
      "expected-format": "YYYY-MM-DD",
      "received": "2024-02-30"
    }
  }]
}
```

---

## DateTime Scalar Errors

### Missing Timezone (Rejected per FR-010)

**Input:**
```graphql
mutation {
  createTask(
    title: "Test Task"
    status: DRAFT
    priority: LOW
    scheduledFor: "2024-01-15T14:30:00"
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "DateTime must include timezone offset",
    "extensions": {
      "received": "2024-01-15T14:30:00",
      "expected-format": "YYYY-MM-DDTHH:MM:SS±HH:MM"
    }
  }]
}
```

### Invalid Timezone Offset

**Input:**
```graphql
mutation {
  createTask(
    title: "Test Task"
    status: DRAFT
    priority: LOW
    scheduledFor: "2024-01-15T14:30:00+99:99"
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid datetime format. Expected ISO8601 with timezone, got: 2024-01-15T14:30:00+99:99",
    "extensions": {
      "expected-format": "YYYY-MM-DDTHH:MM:SS±HH:MM",
      "received": "2024-01-15T14:30:00+99:99"
    }
  }]
}
```

### Invalid Time (25:00:00)

**Input:**
```graphql
mutation {
  createTask(
    title: "Test Task"
    status: DRAFT
    priority: LOW
    scheduledFor: "2024-01-15T25:00:00+00:00"
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid datetime format. Expected ISO8601 with timezone, got: 2024-01-15T25:00:00+00:00",
    "extensions": {
      "expected-format": "YYYY-MM-DDTHH:MM:SS±HH:MM",
      "received": "2024-01-15T25:00:00+00:00"
    }
  }]
}
```

---

## Enum Scalar Errors

### Invalid Value (Not in Options)

**Input:**
```graphql
mutation {
  createTask(
    title: "Test Task"
    status: UNKNOWN
    priority: LOW
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid value 'UNKNOWN'. Valid options: DRAFT, IN_PROGRESS, COMPLETED, CANCELLED",
    "extensions": {
      "value": "UNKNOWN",
      "valid-options": ["DRAFT", "IN_PROGRESS", "COMPLETED", "CANCELLED"],
      "suggestion": null
    }
  }]
}
```

### Case Mismatch (Suggestion Provided per FR-016)

**Input:**
```graphql
mutation {
  createTask(
    title: "Test Task"
    status: draft
    priority: LOW
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid value 'draft'. Valid options: DRAFT, IN_PROGRESS, COMPLETED, CANCELLED. Did you mean 'DRAFT'?",
    "extensions": {
      "value": "draft",
      "valid-options": ["DRAFT", "IN_PROGRESS", "COMPLETED", "CANCELLED"],
      "suggestion": "DRAFT"
    }
  }]
}
```

### Case Mismatch (Multi-word Enum)

**Input:**
```graphql
mutation {
  createTask(
    title: "Test Task"
    status: in_progress
    priority: LOW
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid value 'in_progress'. Valid options: DRAFT, IN_PROGRESS, COMPLETED, CANCELLED. Did you mean 'IN_PROGRESS'?",
    "extensions": {
      "value": "in_progress",
      "valid-options": ["DRAFT", "IN_PROGRESS", "COMPLETED", "CANCELLED"],
      "suggestion": "IN_PROGRESS"
    }
  }]
}
```

---

## Decimal Scalar Errors

### Precision Exceeds Limit (38 digits)

**Input:**
```graphql
mutation {
  createProduct(
    name: "Test Product"
    price: "12345678901234567890123456789012345678.9"
    category: ELECTRONICS
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Decimal precision exceeds limit of 38 (got 39)",
    "extensions": {
      "value": "12345678901234567890123456789012345678.9",
      "precision": 39,
      "max-precision": 38
    }
  }]
}
```

### Scale Exceeds Limit (10 decimal places)

**Input:**
```graphql
mutation {
  createProduct(
    name: "Test Product"
    price: "1.12345678901"
    category: ELECTRONICS
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Decimal scale exceeds limit of 10 (got 11)",
    "extensions": {
      "value": "1.12345678901",
      "scale": 11,
      "max-scale": 10
    }
  }]
}
```

### Invalid Decimal Format

**Input:**
```graphql
mutation {
  createProduct(
    name: "Test Product"
    price: "not-a-number"
    category: ELECTRONICS
  ) {
    id
  }
}
```

**Expected Error:**
```json
{
  "errors": [{
    "message": "Invalid decimal format",
    "extensions": {
      "received": "not-a-number"
    }
  }]
}
```

---

## Schema Generation Errors (Fail Fast per FR-022, FR-023)

### Invalid Scalar Type in Schema Definition

**Scenario:** Developer defines a Malli schema with an unsupported scalar type.

**Code:**
```clojure
(def InvalidSchema
  [:map
   {:ringline/datomic-ns "invalid"}
   [:id :uuid]
   [:unknown-field :unsupported-type]])  ; Unsupported type

(init-framework {:Invalid InvalidSchema} {})
```

**Expected Error:**
```
ExceptionInfo: Schema generation failed for entity :Invalid
{:entity-name :Invalid
 :field :unknown-field
 :malli-type :unsupported-type
 :error "No Datomic type mapping for :unsupported-type"}
```

**Rationale:** Fail fast at schema definition time (not runtime) per spec clarification.
