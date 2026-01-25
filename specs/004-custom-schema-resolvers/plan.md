# Implementation Plan: Custom Query and Mutation Schema Support

**Branch**: `004-custom-schema-resolvers` | **Date**: 2026-01-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-custom-schema-resolvers/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Enable developers to define custom GraphQL queries and mutations using Malli schema properties (`:ringline/custom-query`, `:ringline/custom-mutation`) and attach manually-written resolver functions. Custom operations will merge seamlessly with auto-generated CRUD operations, with custom operations taking precedence on name conflicts. The framework will validate all custom schemas during initialization (fail fast) and support all Malli types with zero breaking changes to existing code.

## Technical Context

**Language/Version**: Clojure 1.12.0
**Primary Dependencies**: Malli 0.20.0 (schema validation), Lacinia 1.3.0-beta-1 (GraphQL), Datomic Free 0.9.5697 (database)
**Storage**: Datomic (in-memory for tests, configurable for production)
**Testing**: Kaocha 1.91.1392
**Target Platform**: JVM
**Project Type**: Single library project
**Performance Goals**: <10% framework initialization overhead when adding custom schemas (from SC-003)
**Constraints**: Zero breaking changes to existing code (from SC-002), fail-fast validation during initialization
**Scale/Scope**: Support 100% of Malli types in custom operations (from SC-004), developers can define custom query in <10 lines (from SC-001)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Namespace-First Architecture ✅ PASS

**Requirement**: Every feature MUST be organized as a self-contained namespace with clear boundaries.

**Compliance**: This feature will extend existing namespaces with clear, single-purpose additions:
- `ringline.schema.properties` - Add new custom property keywords (`:ringline/custom-query`, `:ringline/custom-mutation`)
- `ringline.schema.parser` - Extend to parse custom query/mutation definitions from schema properties
- `ringline.schema.lacinia` - Extend to generate Lacinia schemas from custom operations
- `ringline.core` - Extend `init-framework` to validate resolver attachment and merge custom operations

Each extension maintains the existing namespace's single purpose and can be tested independently.

### II. Data-Driven Design ✅ PASS

**Requirement**: All domain logic MUST operate on immutable data structures.

**Compliance**: Custom query/mutation definitions will be represented as immutable Clojure maps extracted from Malli schema properties. The feature operates purely on data transformation:
- Input: Malli schemas with `:ringline/custom-query` / `:ringline/custom-mutation` properties
- Processing: Parse properties into immutable definition maps
- Output: Lacinia schema maps with merged custom and auto-generated operations
- Resolver attachment: Map of operation names to resolver functions (immutable)

No stateful objects or mutable state required.

### III. Test-First Development (NON-NEGOTIABLE) ✅ PASS

**Requirement**: TDD is mandatory. Write tests → Get user approval → Verify tests fail → Implement code → Verify tests pass.

**Compliance**: Implementation will follow strict TDD workflow:
1. Write contract tests for custom property parsing
2. Write contract tests for custom Lacinia schema generation
3. Write integration tests for resolver attachment validation
4. Write integration tests for custom/auto-generated operation merging
5. Get user approval on test suite
6. Verify all tests fail (red)
7. Implement features to make tests pass (green)
8. Refactor while keeping tests green

Tests will be organized under `test/ringline/schema/` and `test/ringline/integration/`.

### IV. REPL-Driven Development ✅ PASS

**Requirement**: Development MUST leverage the REPL for interactive exploration and validation.

**Compliance**: All new functions will be designed for REPL testing:
- Rich comment blocks with example custom query/mutation definitions
- Functions accept simple data structures (maps, keywords) for easy REPL invocation
- Incremental development: test each parsing/generation step in REPL before integration
- Use existing `tools.namespace` setup for safe reloading

### V. Schema Validation ✅ PASS

**Requirement**: All data entering or leaving system boundaries MUST be validated using Malli schemas.

**Compliance**:
- Custom query/mutation definitions will have explicit Malli schemas (e.g., `CustomQueryDefinition`, `CustomMutationDefinition`)
- Validation during framework initialization (fail fast on missing/invalid fields)
- Resolver attachment validation (ensure all custom operations have resolvers)
- Input/output validation for custom operations uses existing Malli schema infrastructure

### VI. Simplicity & Immutability ✅ PASS

**Requirement**: Start simple, follow YAGNI, prefer pure functions, avoid premature abstraction.

**Compliance**:
- Reuse existing parsing and generation patterns from auto-generated operations
- Simple data structure: custom operations are just maps with `:name`, `:args`, `:return-type`
- No new abstractions: extend existing `parse-schema` and `generate-schema` functions
- Pure functions: all parsing and generation logic is pure data transformation
- Conflict resolution is simple: custom operations override auto-generated (no complex merging logic)

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
├── core.clj                    # Extend init-framework with custom operation support
├── schema/
│   ├── parser.clj              # Extend to parse :ringline/custom-query and :ringline/custom-mutation
│   ├── lacinia.clj             # Extend to generate Lacinia schemas from custom operations
│   ├── properties.clj          # Add custom-query and custom-mutation property definitions
│   └── types.clj               # Add CustomQueryDefinition and CustomMutationDefinition schemas
├── mutation/
│   └── [no changes needed]
├── query/
│   └── [no changes needed]
└── response/
    └── [no changes needed]

test/ringline/
├── schema/
│   ├── parser_test.clj         # Add tests for custom operation parsing
│   ├── lacinia_test.clj        # Add tests for custom operation schema generation
│   └── types_test.clj          # Add tests for custom definition schemas
└── integration/
    ├── custom_operations_test.clj  # NEW: End-to-end tests for custom queries/mutations
    └── resolver_validation_test.clj # NEW: Tests for resolver attachment validation
```

**Structure Decision**: Single library project structure. This feature extends the existing Ringline framework by adding custom operation support to the schema parsing and generation pipeline. We follow the established pattern of organizing code by capability (schema, query, response, mutation) and mirroring the source structure in tests. New integration tests will validate the complete custom operation workflow.

## Complexity Tracking

> **No constitutional violations - this section intentionally left empty**

All constitutional principles are satisfied. No complexity justification required.

---

## Post-Phase 1 Constitution Re-Check

*Re-evaluation after completing research.md, data-model.md, contracts/, and quickstart.md*

### I. Namespace-First Architecture ✅ PASS

**Re-evaluation**: Design maintains namespace boundaries with focused extensions:
- `ringline.schema.properties` - Two new property keywords (`:ringline/custom-query`, `:ringline/custom-mutation`)
- `ringline.schema.parser` - Two new parsing functions (`parse-custom-query`, `parse-custom-mutation`)
- `ringline.schema.lacinia` - Three new generation functions for custom operations
- `ringline.core` - Three new validation/attachment functions

Each addition has a single, clear purpose. No organizational-only namespaces created.

### II. Data-Driven Design ✅ PASS

**Re-evaluation**: All entities defined as immutable maps with Malli schemas:
- `CustomQueryDefinition` - Immutable map with `:name`, `:args`, `:return-type`
- `CustomMutationDefinition` - Immutable map with `:name`, `:args`, `:return-type`
- `ResolverMap` - Immutable map of operation names to functions

Data flow is pure transformation: Malli properties → parsed definitions → Lacinia schemas.

### III. Test-First Development (NON-NEGOTIABLE) ✅ PASS

**Re-evaluation**: Contract tests defined for all new functions:
- `parser-api.edn` - Contracts for `parse-custom-query`, `parse-custom-mutation`
- `lacinia-api.edn` - Contracts for custom schema generation functions
- `core-api.edn` - Contracts for resolver validation and attachment

Integration tests planned:
- `custom_operations_test.clj` - End-to-end custom query/mutation workflow
- `resolver_validation_test.clj` - Fail-fast validation testing

TDD workflow will be strictly followed during implementation.

### IV. REPL-Driven Development ✅ PASS

**Re-evaluation**: `quickstart.md` provides 4 complete REPL-ready examples:
- Example 1: Custom query definition and execution
- Example 2: Custom mutation definition and execution
- Example 3: Mixing auto-generated and custom operations
- Example 4: Custom operation override behavior

All examples use simple data structures testable in REPL. Rich comment blocks will be added during implementation.

### V. Schema Validation ✅ PASS

**Re-evaluation**: Malli schemas defined for all new entities:
- `CustomQueryDefinition` schema in `data-model.md`
- `CustomMutationDefinition` schema in `data-model.md`
- Validation rules documented in `parser-api.edn`
- Fail-fast validation during `init-framework` (documented in `core-api.edn`)

All boundaries validated: schema properties → parsed definitions → Lacinia schemas → resolver attachment.

### VI. Simplicity & Immutability ✅ PASS

**Re-evaluation**: Design follows YAGNI and simplicity principles:
- Reuses existing property pattern (`:ringline/*`)
- Extends existing parser/generator functions (no new abstractions)
- Simple conflict resolution (map merge, custom takes precedence)
- Pure functions for all transformations
- No stateful objects or mutable state

Research document confirms no new dependencies needed. All complexity justified by requirements.

**Final Assessment**: All constitutional principles satisfied. Ready to proceed to Phase 2 (task breakdown).
