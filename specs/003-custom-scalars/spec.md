# Feature Specification: Custom Scalars Support

**Feature Branch**: `003-custom-scalars`
**Created**: 2026-01-24
**Status**: Draft
**Input**: User description: "Add custom scalars: Date and DateTime (in GraphQL these will be ISO8601-formatted strings of length 10 and 25 respectively), enums, and precise decimal numbers"

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Date Field Support (Priority: P1)

As a framework user, I need to define date fields in my Malli schemas so that I can store and query calendar dates (without time information) in my application.

**Why this priority**: Date fields are fundamental to most business applications (birth dates, event dates, deadlines, etc.). This is the most commonly needed temporal data type and provides immediate value.

**Independent Test**: Can be fully tested by defining a Malli schema with a date field, creating entities with date values, querying them via GraphQL, and verifying dates are formatted as ISO8601 strings (YYYY-MM-DD format, 10 characters).

**Acceptance Scenarios**:

1. **Given** a Malli schema with a date field, **When** I create an entity with a date value, **Then** the date is stored and can be queried via GraphQL as an ISO8601 date string
2. **Given** a GraphQL query requesting a date field, **When** the query executes, **Then** the date is returned as a 10-character ISO8601 string (e.g., "2024-01-15")
3. **Given** a date value in the database, **When** I query it via GraphQL, **Then** the date maintains its accuracy without timezone conversion issues

---

### User Story 2 - DateTime Field Support (Priority: P1)

As a framework user, I need to define datetime fields in my Malli schemas so that I can store and query timestamps with full date and time information in my application.

**Why this priority**: DateTime fields are essential for tracking when events occur (created timestamps, updated timestamps, scheduled events). This is equally critical as date-only fields for most applications.

**Independent Test**: Can be fully tested by defining a Malli schema with a datetime field, creating entities with datetime values, querying them via GraphQL, and verifying datetimes are formatted as ISO8601 strings with timezone (25 characters).

**Acceptance Scenarios**:

1. **Given** a Malli schema with a datetime field, **When** I create an entity with a datetime value, **Then** the datetime is stored and can be queried via GraphQL as an ISO8601 datetime string
2. **Given** a GraphQL query requesting a datetime field, **When** the query executes, **Then** the datetime is returned as a 25-character ISO8601 string (e.g., "2024-01-15T14:30:00+00:00")
3. **Given** a datetime value with timezone information, **When** I query it via GraphQL, **Then** the timezone information is preserved in the response

---

### User Story 3 - Enum Field Support (Priority: P2)

As a framework user, I need to define enum fields in my Malli schemas so that I can restrict field values to a predefined set of options in my application.

**Why this priority**: Enums are common for status fields, categories, and other constrained choices. While important, they're less universally needed than date/datetime fields.

**Independent Test**: Can be fully tested by defining a Malli schema with an enum field, creating entities with enum values, querying them via GraphQL, and verifying only valid enum values are accepted.

**Acceptance Scenarios**:

1. **Given** a Malli schema with an enum field defining allowed values, **When** I create an entity with a valid enum value, **Then** the value is stored and can be queried via GraphQL
2. **Given** a GraphQL query requesting an enum field, **When** the query executes, **Then** the enum value is returned as a string matching one of the defined options
3. **Given** an attempt to create an entity with an invalid enum value, **When** the mutation executes, **Then** the system rejects the value with a clear validation error

---

### User Story 4 - Decimal Number Support (Priority: P2)

As a framework user, I need to define decimal number fields in my Malli schemas so that I can store precise numeric values (like currency amounts or measurements) without floating-point precision errors.

**Why this priority**: Decimal precision is critical for financial applications and scientific measurements, but not all applications need it. Integer and basic numeric types may suffice for many use cases.

**Independent Test**: Can be fully tested by defining a Malli schema with a decimal field, creating entities with decimal values, querying them via GraphQL, and verifying precision is maintained without rounding errors.

**Acceptance Scenarios**:

1. **Given** a Malli schema with a decimal field, **When** I create an entity with a precise decimal value (e.g., 19.99), **Then** the value is stored without precision loss
2. **Given** a GraphQL query requesting a decimal field, **When** the query executes, **Then** the decimal value is returned with full precision maintained
3. **Given** a decimal value with many decimal places, **When** I store and retrieve it, **Then** no rounding errors occur

---

### Edge Cases

- What happens when a date string is provided in an invalid format (e.g., "01/15/2024" instead of "2024-01-15")?
- System MUST reject datetime strings provided without timezone information with a clear validation error
- What happens when a datetime string has an invalid timezone offset?
- System accepts any valid ISO8601 date without year range restrictions (e.g., year 0001, 9999, or beyond if valid)
- System MUST reject enum values that don't match defined options (case-sensitive); error messages MUST list valid options and suggest correctly-cased alternatives when case mismatch detected
- System MUST reject decimal values that exceed precision limit of 38 total digits or scale limit of 10 decimal places with a clear error message
- What happens when a null value is provided for a required date/datetime/enum/decimal field?
- How are date/datetime values handled across different timezones in queries and mutations?

## Requirements _(mandatory)_

### Functional Requirements

#### Date Scalar Requirements

- **FR-001**: System MUST support date fields in Malli schemas that represent calendar dates without time information
- **FR-002**: System MUST serialize date values as ISO8601-formatted strings with exactly 10 characters (YYYY-MM-DD format)
- **FR-003**: System MUST accept and persist ISO8601 date strings
- **FR-004**: System MUST validate that date strings conform to ISO8601 format before accepting them; any valid ISO8601 date is accepted without year range restrictions
- **FR-005**: System MUST reject invalid date values (e.g., February 30th, invalid month numbers) with clear error messages

#### DateTime Scalar Requirements

- **FR-006**: System MUST support datetime fields in Malli schemas that represent timestamps with date, time, and timezone information
- **FR-007**: System MUST serialize datetime values as ISO8601-formatted strings with exactly 25 characters (YYYY-MM-DDTHH:MM:SS±HH:MM format)
- **FR-008**: System MUST accept and persist ISO8601 datetime strings with timezone information
- **FR-009**: System MUST store datetime values in UTC when saving and retrieving datetime values
- **FR-010**: System MUST validate that datetime strings conform to ISO8601 format with timezone before accepting them; datetime strings without timezone information MUST be rejected with a validation error
- **FR-011**: System MUST reject invalid datetime values (e.g., 25:00:00, invalid timezone offsets) with clear error messages

#### Enum Scalar Requirements

- **FR-012**: System MUST support enum fields in Malli schemas that restrict values to a predefined set of options
- **FR-013**: System MUST validate that enum values match one of the defined options before accepting them
- **FR-014**: System MUST perform case-sensitive matching for enum values
- **FR-015**: System MUST serialize enum values as strings in GraphQL responses
- **FR-016**: System MUST provide clear error messages when invalid enum values are provided, listing the valid options; when a case mismatch is detected (e.g., "active" vs "ACTIVE"), the error message MUST include a suggestion for the correctly-cased option

#### Decimal Number Requirements

- **FR-017**: System MUST support decimal number fields in Malli schemas for precise numeric values
- **FR-018**: System MUST maintain full precision of decimal values without floating-point rounding errors
- **FR-019**: System MUST serialize decimal values as numbers in GraphQL responses
- **FR-020**: System MUST accept and persist numeric values from GraphQL with full precision
- **FR-021**: System MUST validate that decimal values are within precision limit of 38 digits total and scale limit of 10 digits after the decimal point; values exceeding these limits MUST be rejected with a clear error message

#### Integration Requirements

- **FR-022**: System MUST automatically generate appropriate GraphQL scalar types for date, datetime, enum, and decimal fields defined in Malli schemas; schema generation failures MUST fail fast at schema definition time with clear error messages indicating the cause
- **FR-023**: System MUST automatically generate appropriate database schema definitions for date, datetime, enum, and decimal fields; schema generation failures MUST fail fast at schema definition time with clear error messages
- **FR-024**: System MUST support date, datetime, enum, and decimal fields in all CRUD operations (create, read, update, delete)
- **FR-025**: System MUST support querying and filtering by date, datetime, enum, and decimal fields
- **FR-026**: System MUST support date, datetime, enum, and decimal fields in nested entity relationships

### Key Entities _(include if feature involves data)_

- **Date Scalar**: Represents a calendar date without time information. Serialized as ISO8601 string (10 characters: YYYY-MM-DD). Used for birth dates, event dates, deadlines, etc.

- **DateTime Scalar**: Represents a timestamp with date, time, and timezone information. Serialized as ISO8601 string (25 characters: YYYY-MM-DDTHH:MM:SS±HH:MM). Used for created/updated timestamps, scheduled events, etc.

- **Enum Scalar**: Represents a value constrained to a predefined set of options. Serialized as a string matching one of the allowed values. Used for status fields, categories, types, etc.

- **Decimal Scalar**: Represents a precise numeric value without floating-point errors. Serialized as a number with full precision maintained. Used for currency amounts, measurements, percentages, etc.

## Success Criteria _(mandatory)_

### Measurable Outcomes

- **SC-001**: Framework users can define schemas with date, datetime, enum, and decimal fields and have them work correctly in all CRUD operations without additional configuration
- **SC-002**: All date values are consistently formatted as 10-character ISO8601 strings (YYYY-MM-DD) in GraphQL responses
- **SC-003**: All datetime values are consistently formatted as 25-character ISO8601 strings (YYYY-MM-DDTHH:MM:SS±HH:MM) in GraphQL responses with timezone preserved
- **SC-004**: Decimal values maintain full precision through the entire data flow (GraphQL → storage → GraphQL) with zero rounding errors for values within reasonable precision limits
- **SC-005**: Invalid date, datetime, enum, or decimal values are rejected with clear, actionable error messages that specify the expected format or valid options; schema generation failures are detected immediately at definition time, not at runtime
- **SC-006**: Existing framework features (automatic schema generation, CRUD mutations, query resolvers) work seamlessly with the new scalar types without breaking changes
- **SC-007**: Framework users can successfully migrate existing applications to use the new scalar types without data loss or corruption

## Clarifications

### Session 2026-01-24

- Q: How should the system handle datetime values submitted without timezone information? → A: Reject with validation error requiring timezone
- Q: What are the maximum precision and scale limits for decimal values? → A: Precision 38, Scale 10
- Q: What date range should the system support? → A: No range limits (accept any valid ISO8601 date)
- Q: Should enum validation errors include suggestions for similar values (e.g., did you mean "ACTIVE" instead of "active")? → A: Yes, include suggestions for case mismatches
- Q: How should the system handle schema generation failures for custom scalar fields? → A: Fail fast at schema definition time with clear error

## Assumptions

- Date and datetime values will use standard ISO8601 formatting conventions
- The 10-character date format assumes YYYY-MM-DD (e.g., "2024-01-15")
- The 25-character datetime format assumes YYYY-MM-DDTHH:MM:SS±HH:MM (e.g., "2024-01-15T14:30:00+00:00")
- Enum values are case-sensitive by default (common GraphQL convention)
- Decimal precision is limited to 38 total digits with maximum 10 digits after the decimal point, sufficient for common use cases (currency, measurements, percentages)
- Timezone handling for datetime values will preserve the original timezone rather than converting to a standard timezone
- Date values represent calendar dates and are not affected by timezone considerations
- The framework will handle conversion between GraphQL representations and database storage formats transparently
