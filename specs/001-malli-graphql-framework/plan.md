# Implementation Plan: Malli-GraphQL Framework

**Branch**: `001-malli-graphql-framework` | **Date**: 2026-01-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-malli-graphql-framework/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a Clojure framework that uses Malli schemas as the single source of truth for defining data models, then automatically generates both Datomic database schemas and Lacinia GraphQL schemas. The framework will handle the complete request-response cycle by converting incoming GraphQL queries to Datomic pull patterns and transforming Datomic results back to GraphQL-compatible responses. This eliminates the need for developers to manually maintain three separate schema definitions and ensures consistency across the data layer, database, and API.

## Technical Context

**Language/Version**: Clojure 1.12.0
**Primary Dependencies**: Malli 0.20.0 (schema definition), Lacinia 1.3.0-beta-1 (GraphQL), Datomic Free 0.9.5697 (database)
**Storage**: Datomic (in-memory or persistent peer storage)
**Testing**: Kaocha 1.91.1392
**Target Platform**: JVM (Java 11+), Ring-compatible web servers
**Project Type**: Single library project
**Performance Goals**: Schema generation < 1 second for 50 entities, query conversion < 10ms per query
**Constraints**: Must work with existing Ring middleware, must support up to 5 levels of nested relationships
**Scale/Scope**: Framework library (not end-user application), designed to handle data models with 10-100 entities

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Namespace-First Architecture ✅ PASS

**Requirement**: Every feature MUST be organized as a self-contained namespace with clear boundaries.

**Compliance**: The framework will be organized into distinct namespaces:
- `ringline.schema.parser` - Malli schema parsing
- `ringline.schema.datomic` - Datomic schema generation
- `ringline.schema.lacinia` - Lacinia schema generation
- `ringline.query.converter` - GraphQL to Datomic query conversion
- `ringline.response.transformer` - Datomic to GraphQL response transformation

Each namespace has a single, well-defined purpose and can be tested independently.

### II. Data-Driven Design ✅ PASS

**Requirement**: All domain logic MUST operate on immutable data structures. Business entities MUST be represented as plain Clojure maps.

**Compliance**: The framework operates entirely on immutable data:
- Malli schemas are data structures (maps and vectors)
- Generated Datomic schemas are data (transaction maps)
- Generated Lacinia schemas are data (EDN maps)
- All transformations are pure functions operating on immutable data
- No stateful objects or mutable state

### III. Test-First Development (NON-NEGOTIABLE) ✅ PASS

**Requirement**: TDD mandatory - Write tests → Get user approval → Tests fail → Implement → Tests pass.

**Compliance**: Development will follow strict TDD:
- Contract tests for each namespace's public API
- Integration tests for schema generation pipelines
- Unit tests for type mapping and conversion functions
- All tests written before implementation
- Kaocha test runner configured

### IV. REPL-Driven Development ✅ PASS

**Requirement**: Development MUST leverage the REPL. All namespaces MUST be reloadable.

**Compliance**: Framework design supports REPL workflow:
- All functions are pure and easily testable from REPL
- No global state that prevents reloading
- Rich comment blocks will demonstrate usage
- Example Malli schemas for interactive testing
- tools.namespace for safe reloading

### V. Schema Validation ✅ PASS

**Requirement**: All data entering or leaving system boundaries MUST be validated using Malli schemas.

**Compliance**: This framework IS about Malli schema validation:
- Input Malli schemas will be validated for correctness
- Generated Datomic schemas will be validated before return
- Generated Lacinia schemas will be validated before return
- Framework's own API will use Malli schemas for validation
- Perfect alignment with constitutional requirement

### VI. Simplicity & Immutability ✅ PASS

**Requirement**: Start simple, YAGNI principles, prefer pure functions.

**Compliance**: Framework follows simplicity principles:
- Pure functions for all transformations
- No premature optimization
- Simple data-in, data-out design
- Immutable data structures throughout
- Complexity only where necessary (type mapping, relationship resolution)

### Pre-Implementation Gate: ✅ ALL CHECKS PASS

No constitutional violations. Framework design aligns perfectly with all six core principles. Proceeding to Phase 0 research.

## Project Structure

### Documentation (this feature)

```text
specs/001-malli-graphql-framework/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── schema-api.edn   # Framework's public API contract
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/ringline/
├── core.clj                    # Main entry point (existing)
├── schema/
│   ├── parser.clj              # Parse Malli schemas, extract metadata
│   ├── datomic.clj             # Generate Datomic schema from Malli
│   └── lacinia.clj             # Generate Lacinia schema from Malli
├── query/
│   └── converter.clj           # Convert GraphQL queries to Datomic pull
└── response/
    └── transformer.clj         # Convert Datomic results to GraphQL format

test/ringline/
├── schema/
│   ├── parser_test.clj         # Contract tests for schema parsing
│   ├── datomic_test.clj        # Contract tests for Datomic generation
│   └── lacinia_test.clj        # Contract tests for Lacinia generation
├── query/
│   └── converter_test.clj      # Contract tests for query conversion
├── response/
│   └── transformer_test.clj    # Contract tests for response transformation
└── integration/
    ├── schema_generation_test.clj      # End-to-end schema generation
    └── query_execution_test.clj        # End-to-end query flow
```

**Structure Decision**: Single library project structure. This is a framework library, not an application, so we use the standard Clojure library layout with `src/ringline/` for source code and `test/ringline/` mirroring the source structure. The framework is organized into three main capability areas (schema, query, response) each in their own namespace directory.

## Complexity Tracking

> **No constitutional violations - this section intentionally left empty**

All constitutional principles are satisfied. No complexity justification required.

---

## Post-Phase 1 Constitution Re-Check

*Re-evaluation after completing research.md, data-model.md, contracts/, and quickstart.md*

### I. Namespace-First Architecture ✅ PASS

**Re-evaluation**: Design artifacts confirm namespace organization:
- API contract (schema-api.edn) defines 6 distinct namespaces
- Each namespace has clear, single-purpose API
- Data model shows no cross-namespace coupling
- Quickstart demonstrates independent namespace usage

### II. Data-Driven Design ✅ PASS

**Re-evaluation**: Data model confirms immutable data approach:
- All framework entities are plain maps (ParsedSchema, FieldDefinition, etc.)
- No stateful objects in design
- State transitions documented as data transformations
- Quickstart examples show pure data flow

### III. Test-First Development (NON-NEGOTIABLE) ✅ PASS

**Re-evaluation**: Project structure includes comprehensive test organization:
- Contract tests for each namespace
- Integration tests for end-to-end flows
- Test structure mirrors source structure
- Quickstart includes testing examples

### IV. REPL-Driven Development ✅ PASS

**Re-evaluation**: Quickstart demonstrates REPL workflow:
- Step-by-step REPL examples provided
- All functions testable interactively
- Rich comment blocks planned
- No global state prevents reloading

### V. Schema Validation ✅ PASS

**Re-evaluation**: API contract and data model confirm validation:
- All framework entities have Malli type definitions
- Input/output validation at all boundaries
- Framework's own API uses Malli schemas
- Perfect constitutional alignment

### VI. Simplicity & Immutability ✅ PASS

**Re-evaluation**: Design maintains simplicity:
- Pure functions throughout (confirmed in API contract)
- No premature abstractions
- Simple data-in, data-out pattern
- Complexity limited to necessary type mappings

### Post-Design Gate: ✅ ALL CHECKS PASS

Design phase complete. All constitutional principles satisfied. No violations introduced during detailed design. Ready to proceed to Phase 2 (task breakdown via `/speckit.tasks` command).
