# Implementation Plan: Custom Scalars Support

**Branch**: `003-custom-scalars` | **Date**: 2026-01-24 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-custom-scalars/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Add support for four custom scalar types (Date, DateTime, Enum, Decimal) to the Ringline framework's Malli-to-GraphQL/Datomic schema generation pipeline. These scalars will extend the existing type mapping system to handle temporal data, constrained values, and precise numeric calculations while maintaining the framework's single-source-of-truth philosophy.

## Technical Context

**Language/Version**: Clojure 1.12.0
**Primary Dependencies**: Malli 0.20.0 (schema validation), Lacinia 1.3.0-beta-1 (GraphQL), Datomic Free 0.9.5697 (database)
**Storage**: Datomic (in-memory for tests, configurable for production)
**Testing**: Kaocha 1.91.1392 (test runner)
**Target Platform**: JVM
**Project Type**: Single (library/framework)
**Performance Goals**: NEEDS CLARIFICATION (GraphQL scalar serialization/deserialization performance benchmarks)
**Constraints**: Must maintain backward compatibility with existing type system; fail fast at schema definition time (not runtime); precision limit 38 digits total, scale limit 10 decimal places for Decimal type
**Scale/Scope**: 4 new scalar types (Date, DateTime, Enum, Decimal); extend 5-7 core namespaces (types, parser, datomic, lacinia, mutation/transaction, mutation/lacinia, response/transformer); add validation and conversion logic

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

### I. Namespace-First Architecture ✅

**Status**: PASS
**Rationale**: Custom scalar logic will be organized into focused namespaces:

- Extend existing `ringline.schema.types` for type mappings
- Extend existing `ringline.schema.parser`, `ringline.schema.datomic`, `ringline.schema.lacinia` for schema generation
- Extend existing `ringline.mutation.transaction` and `ringline.response.transformer` for value conversion
- Potentially add new `ringline.schema.scalars` namespace for custom scalar definitions and validation logic

Each namespace maintains single responsibility and clear boundaries.

### II. Data-Driven Design ✅

**Status**: PASS
**Rationale**: All scalar type definitions, validation rules, and conversion logic will operate on immutable data structures (maps, vectors). Type mappings are pure data (maps). Validation functions are pure (input → validation result). Conversion functions are pure (value + type → converted value). No stateful objects introduced.

### III. Test-First Development (NON-NEGOTIABLE) ✅

**Status**: PASS
**Rationale**: TDD workflow will be strictly followed:

1. Write tests for each scalar type (Date, DateTime, Enum, Decimal)
2. Verify tests fail (Red)
3. Implement type mappings, validation, conversion logic
4. Verify tests pass (Green)
5. Refactor for clarity and performance

Tests will cover: type mapping, validation (valid/invalid inputs), serialization, deserialization, edge cases (timezone handling, precision limits, case sensitivity, year ranges).

### IV. REPL-Driven Development ✅

**Status**: PASS
**Rationale**: All new functions will be REPL-testable:

- Type conversion functions can be tested with sample values
- Validation functions can be tested with valid/invalid inputs
- Schema generation can be tested with sample Malli schemas
- Rich comment blocks will demonstrate usage of Date, DateTime, Enum, Decimal types

Namespaces remain reloadable without REPL restart.

### V. Schema Validation ✅

**Status**: PASS
**Rationale**: All new scalar types will be validated using Malli schemas:

- Date/DateTime: ISO8601 format validation with regex patterns
- Enum: Validation against predefined option sets
- Decimal: Precision/scale validation
- GraphQL inputs: Validated before conversion to Datomic values
- Datomic results: Validated before serialization to GraphQL

Validation occurs at system boundaries (GraphQL → framework, framework → Datomic).

### VI. Simplicity & Immutability ✅

**Status**: PASS
**Rationale**: Implementation follows YAGNI:

- Start with minimal type mappings (4 scalar types only)
- Use existing Java Time interop (no new date library)
- Leverage Datomic's built-in types where possible (`:db.type/instant`, `:db.type/bigdec`)
- Pure functions for all conversions (no mutable state)
- Fail fast on invalid schemas (simple error handling)

No premature abstraction. Complexity justified only where needed (e.g., ISO8601 parsing requires specific format validation).

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/ringline/
├── schema/
│   ├── types.clj           # EXTEND: Add Date, DateTime, Enum, Decimal type mappings
│   ├── parser.clj          # EXTEND: Handle new scalar types in field parsing
│   ├── datomic.clj         # EXTEND: Generate Datomic schemas for new scalars
│   ├── lacinia.clj         # EXTEND: Generate Lacinia custom scalar definitions
│   ├── scalars.clj         # NEW: Custom scalar validation and conversion logic
│   └── properties.clj      # (existing, may need minor updates)
├── mutation/
│   ├── transaction.clj     # EXTEND: Implement value conversions (GraphQL → Datomic)
│   └── lacinia.clj         # EXTEND: Handle new scalar types in mutation args
├── response/
│   └── transformer.clj     # EXTEND: Implement serialization (Datomic → GraphQL)
└── core.clj                # (existing, minimal changes if any)

test/ringline/
├── schema/
│   ├── types_test.clj      # EXTEND: Test new type mappings
│   ├── parser_test.clj     # EXTEND: Test parsing of new scalar types
│   ├── datomic_test.clj    # EXTEND: Test Datomic schema generation
│   ├── lacinia_test.clj    # EXTEND: Test Lacinia schema generation
│   └── scalars_test.clj    # NEW: Test validation and conversion logic
├── mutation/
│   ├── transaction_test.clj # EXTEND: Test value conversions
│   └── lacinia_test.clj    # EXTEND: Test mutation arg handling
├── response/
│   └── transformer_test.clj # EXTEND: Test serialization
└── integration/
    └── custom_scalars_integration_test.clj  # NEW: End-to-end CRUD tests
```

**Structure Decision**: Single project structure (Clojure library). This feature extends existing namespaces in the `ringline.schema`, `ringline.mutation`, and `ringline.response` packages. One new namespace (`ringline.schema.scalars`) will be added for custom scalar-specific logic. Tests mirror the source structure under `test/ringline/`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitutional violations. All principles satisfied.

---

## Constitution Check (Post-Design Re-evaluation)

_Re-evaluated after Phase 1 design completion_

### I. Namespace-First Architecture ✅

**Status**: PASS (Confirmed)
**Design Validation**:

- New namespace `ringline.schema.scalars` created for custom scalar logic
- Existing namespaces extended cleanly without violating boundaries
- Each namespace maintains single responsibility
- No organizational-only namespaces created

### II. Data-Driven Design ✅

**Status**: PASS (Confirmed)
**Design Validation**:

- All scalar definitions use immutable data structures (maps for type mappings)
- Validation functions are pure (input → result)
- Conversion functions are pure (value + type → converted value)
- No stateful objects introduced in design

### III. Test-First Development (NON-NEGOTIABLE) ✅

**Status**: PASS (Confirmed)
**Design Validation**:

- Test structure defined in Project Structure section
- Each scalar type has dedicated test coverage
- Integration tests planned for end-to-end CRUD operations
- Edge cases from spec mapped to test scenarios
- TDD workflow will be enforced during implementation

### IV. REPL-Driven Development ✅

**Status**: PASS (Confirmed)
**Design Validation**:

- All conversion functions are REPL-testable with sample values
- Validation functions can be tested interactively
- Schema generation can be tested with sample Malli schemas
- Rich comment blocks planned for quickstart examples
- No REPL-hostile patterns introduced

### V. Schema Validation ✅

**Status**: PASS (Confirmed)
**Design Validation**:

- All scalar types validated using Malli schemas (`:time/local-date`, `:time/offset-date-time`, `:enum`, custom `:decimal`)
- GraphQL inputs validated before conversion to Datomic
- Datomic results validated before serialization to GraphQL
- Validation occurs at all system boundaries
- Custom `:decimal` schema implements `IntoSchema` protocol for full Malli integration

### VI. Simplicity & Immutability ✅

**Status**: PASS (Confirmed)
**Design Validation**:

- Minimal type mappings (4 scalar types only, no over-engineering)
- Uses existing Java Time interop (no new date library)
- Leverages Datomic's built-in types (`:db.type/instant`, `:db.type/bigdec`, `:db.type/keyword`)
- Pure functions for all conversions
- Fail fast on invalid schemas (simple error handling)
- Complexity justified only where needed (ISO8601 parsing, BigDecimal validation)

**Post-Design Conclusion**: All constitutional principles satisfied. Design is ready for implementation.

---

## Phase 0: Research & Unknowns

### Unknowns from Technical Context

1. **Performance Goals**: GraphQL scalar serialization/deserialization performance benchmarks
   - Need to research: Expected performance characteristics for custom scalars in Lacinia
   - Need to research: Performance impact of ISO8601 parsing/formatting
   - Need to research: BigDecimal performance considerations

### Research Tasks

The following research tasks will resolve unknowns and establish best practices:

1. **Malli Custom Type Representation**
   - How to represent Date/DateTime/Decimal in Malli schemas (custom types vs properties vs existing types)
   - Malli's built-in support for temporal and numeric types
   - Best practices for extending Malli's type system

2. **Lacinia Custom Scalar Definitions**
   - How to define custom scalars in Lacinia
   - Serialization and deserialization (parse/serialize functions)
   - Error handling for invalid scalar values
   - Integration with existing Lacinia schema generation

3. **Datomic Type Choices**
   - Best Datomic type for Date (`:db.type/instant` vs alternatives)
   - Best Datomic type for DateTime (`:db.type/instant` with timezone preservation)
   - Best Datomic type for Decimal (`:db.type/bigdec` availability and usage)
   - Best Datomic type for Enum (`:db.type/keyword` vs `:db.type/string`)

4. **ISO8601 Parsing and Formatting**
   - Java Time interop in Clojure (java.time.LocalDate, java.time.OffsetDateTime)
   - ISO8601 format validation (regex patterns for 10-char date, 25-char datetime)
   - Timezone handling (parsing, preservation, serialization)
   - Libraries: clojure.instant, java.time, or custom implementation

5. **Decimal Precision and Scale**
   - BigDecimal usage in Clojure
   - Precision/scale validation strategies
   - Datomic `:db.type/bigdec` capabilities and limitations
   - GraphQL number serialization (maintaining precision)

6. **Enum Validation and Error Messages**
   - Case-sensitive enum matching
   - Case mismatch detection algorithms
   - Error message formatting with suggestions
   - Malli `:enum` type vs custom validation

7. **Performance Benchmarks**
   - Typical Lacinia custom scalar performance
   - ISO8601 parsing performance (java.time vs alternatives)
   - BigDecimal arithmetic performance
   - Acceptable latency targets for scalar conversion

### Research Completion Status

✅ **Phase 0 Complete**: All research tasks completed and documented in `research.md`

**Key Decisions Made:**

1. Use Malli experimental time types (`:time/local-date`, `:time/offset-date-time`)
2. Implement custom `:decimal` schema with precision/scale validation
3. Use built-in `:enum` type for enum scalars
4. Define Lacinia custom scalars with parse/serialize functions
5. Store absolute time in `:db.type/instant` (no timezone offset). Return times as UTC.
6. Target <1ms per scalar conversion for performance

**Unknowns Resolved:**

- Performance goals: <1ms per scalar conversion (acceptable for web API latency)
- All technical unknowns from Technical Context section resolved

---

## Phase 1: Design & Contracts

### Phase 1 Completion Status

✅ **Phase 1 Complete**: All design artifacts generated

**Generated Artifacts:**

1. **`data-model.md`** ✅
   - Malli schema representations for all 4 scalar types
   - Validation rules with examples
   - Type conversion functions (parse, store, serialize)
   - Datomic schema definitions

2. **`contracts/graphql-schema.graphql`** ✅
   - Custom scalar definitions (Date, DateTime, Decimal)
   - Enum type definitions (TaskStatus, TaskPriority, ProductCategory)
   - Object types demonstrating scalar usage (Event, Task, Product)
   - Query and Mutation roots with scalar arguments

3. **`contracts/example-queries.graphql`** ✅
   - Date scalar examples (queries and mutations)
   - DateTime scalar examples (timezone handling)
   - Enum scalar examples (case-sensitive validation)
   - Decimal scalar examples (precision preservation)
   - Combined examples using multiple scalar types

4. **`contracts/error-examples.md`** ✅
   - Date validation errors (invalid format, invalid dates)
   - DateTime validation errors (missing timezone, invalid timezone)
   - Enum validation errors (invalid values, case mismatches with suggestions)
   - Decimal validation errors (precision/scale limits)
   - Schema generation errors (fail fast examples)

5. **`quickstart.md`** ✅
   - How to use Date fields in Malli schemas
   - How to use DateTime fields with timezone
   - How to define Enum fields
   - How to use Decimal fields for currency/measurements
   - Complete example with all scalar types
   - Error handling guidance

6. **Agent Context Update** ✅
   - Ran `.specify/scripts/bash/update-agent-context.sh auggie`
   - Updated `.augment/rules/specify-rules.md` with:
     - Language: Clojure 1.12.0
     - Frameworks: Malli 0.20.0, Lacinia 1.3.0-beta-1, Datomic Free 0.9.5697
     - Database: Datomic (in-memory for tests)

---

## Phase 2: Task Breakdown

**Status**: NOT STARTED (Phase 2 is handled by `/speckit.tasks` command)

The `/speckit.plan` command ends after Phase 1. To proceed with implementation:

1. Run `/speckit.tasks` to generate task breakdown in `tasks.md`
2. Follow TDD workflow: Write tests → Implement → Verify
3. Reference `data-model.md` and `contracts/` for implementation details

---

## Summary

### Planning Complete ✅

**Branch**: `003-custom-scalars`
**Spec**: `specs/003-custom-scalars/spec.md`
**Plan**: `specs/003-custom-scalars/plan.md` (this file)

**Artifacts Generated:**

- ✅ `research.md` - Research findings and technical decisions
- ✅ `data-model.md` - Scalar type definitions and conversions
- ✅ `contracts/graphql-schema.graphql` - GraphQL schema with custom scalars
- ✅ `contracts/example-queries.graphql` - Example queries and mutations
- ✅ `contracts/error-examples.md` - Error message examples
- ✅ `quickstart.md` - User guide for custom scalars
- ✅ Agent context updated

**Constitution Check**: ✅ PASS (all principles satisfied)

**Next Steps:**

1. Run `/speckit.tasks` to generate implementation task breakdown
2. Begin TDD implementation following task order
3. Reference design artifacts during implementation

**Key Technical Decisions:**

- Malli Types: `:time/local-date`, `:time/offset-date-time`, `:enum`, custom `:decimal`
- Datomic Types: `:db.type/instant`, `:db.type/keyword`, `:db.type/bigdec`
- Lacinia: Custom scalars with parse/serialize functions
- DateTime: Store absolute time in `:db.type/instant`, return as UTC
- Performance: Target <1ms per scalar conversion

**Open Risks:**

1. Decimal scale consistency must be enforced in application layer
2. Performance benchmarking needed to validate <1ms target

---

**Planning Phase Complete** - Ready for `/speckit.tasks` command
