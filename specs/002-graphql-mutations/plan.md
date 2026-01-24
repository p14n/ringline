# Implementation Plan: GraphQL Mutations

**Branch**: `002-graphql-mutations` | **Date**: 2026-01-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-graphql-mutations/spec.md`

## Summary

This feature extends the Ringline Malli-GraphQL Framework with mutation support, enabling developers to define create, update, and delete operations for their entities using Malli schemas. The implementation will add mutation parsing, Lacinia mutation schema generation, GraphQL-to-Datomic transaction conversion, and mutation execution with error handling. This builds on the existing query-only framework (feature 001) while preserving all existing functionality.

## Technical Context

**Language/Version**: Clojure 1.12.0
**Primary Dependencies**: Malli 0.20.0 (schema validation), Lacinia 1.3.0-beta-1 (GraphQL), Datomic Free 0.9.5697 (database)
**Storage**: Datomic Free 0.9.5697 (immutable database with datalog queries)
**Testing**: Kaocha 1.91.1392 (test runner), TDD methodology (mandatory per constitution)
**Target Platform**: JVM (via asdf-managed Java installation at /Users/dean.chapman/.asdf/shims/)
**Project Type**: Single library project (Clojure framework)
**Performance Goals**: Mutation execution <500ms for simple operations (per SC-003)
**Constraints**: Must preserve existing query functionality (FR-011), maintain data integrity (SC-005)
**Scale/Scope**: Extends existing framework with 4 new namespaces for mutation support

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Namespace-First Architecture ✅

**Compliance**: PASS
**Plan**: Create self-contained namespaces for mutation support:
- `ringline.mutation.parser` - Parse mutation definitions from Malli schemas
- `ringline.mutation.lacinia` - Generate Lacinia mutation schemas
- `ringline.mutation.transaction` - Convert GraphQL inputs to Datomic transactions
- `ringline.mutation.executor` - Execute mutations and handle responses

Each namespace will have a single, well-defined purpose and be independently testable.

### II. Data-Driven Design ✅

**Compliance**: PASS
**Plan**: All mutation logic operates on immutable data structures:
- Mutation definitions represented as plain maps with Malli schemas
- Transaction data as Datomic transaction vectors
- Mutation results as maps with `:success`, `:data`, `:errors` keys
- Pure functions for all transformations (schema → mutations, inputs → transactions)

### III. Test-First Development (NON-NEGOTIABLE) ✅

**Compliance**: PASS
**Plan**: Strict TDD workflow for all 4 user stories:
1. Write tests for mutation parser → verify failure → implement → verify pass
2. Write tests for Lacinia schema generation → verify failure → implement → verify pass
3. Write tests for transaction conversion → verify failure → implement → verify pass
4. Write tests for mutation execution → verify failure → implement → verify pass

Tests organized under `test/ringline/mutation/` mirroring source structure. Kaocha for test execution.

### IV. REPL-Driven Development ✅

**Compliance**: PASS
**Plan**: All namespaces designed for REPL interaction:
- Functions testable from REPL with example data
- Rich comment blocks with usage examples
- Use `tools.namespace` for safe reloading
- Test fixtures in `test/ringline/fixtures.clj` for REPL experimentation

### V. Schema Validation ✅

**Compliance**: PASS
**Plan**: Malli validation at all boundaries:
- Mutation definition schemas (validate custom properties)
- Mutation input schemas (validate GraphQL inputs against entity schemas)
- Transaction data schemas (validate Datomic transaction structure)
- Mutation result schemas (validate response format)

### VI. Simplicity & Immutability ✅

**Compliance**: PASS
**Plan**: Start simple, add complexity only when needed:
- Support basic CRUD operations (create, update, delete) - no complex workflows initially
- Pure functions for all transformations
- No premature abstraction - implement concrete solutions first
- Immutable data throughout - no stateful mutation tracking

**Overall Status**: ✅ **ALL GATES PASSED** - Ready for Phase 0 research

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
├── core.clj                    # [EXISTING] High-level framework API
├── schema/                     # [EXISTING] Query-related schema processing
│   ├── parser.clj             # [EXISTING] Parse Malli schemas
│   ├── properties.clj         # [EXISTING] Custom Malli properties
│   ├── types.clj              # [EXISTING] Type definitions
│   ├── datomic.clj            # [EXISTING] Datomic schema generation
│   └── lacinia.clj            # [EXISTING] Lacinia query schema generation
├── query/                      # [EXISTING] Query processing
│   └── converter.clj          # [EXISTING] GraphQL → Datomic pull patterns
├── response/                   # [EXISTING] Response transformation
│   └── transformer.clj        # [EXISTING] Datomic → GraphQL format
└── mutation/                   # [NEW] Mutation support
    ├── parser.clj             # [NEW] Parse mutation definitions from Malli
    ├── lacinia.clj            # [NEW] Generate Lacinia mutation schemas
    ├── transaction.clj        # [NEW] Convert GraphQL inputs → Datomic transactions
    └── executor.clj           # [NEW] Execute mutations and format responses

test/ringline/
├── fixtures.clj               # [EXISTING] Test schemas and data
├── core_test.clj              # [EXISTING] Framework API tests
├── schema/                    # [EXISTING] Schema tests
│   ├── parser_test.clj
│   ├── datomic_test.clj
│   └── lacinia_test.clj
├── query/                     # [EXISTING] Query tests
│   └── converter_test.clj
├── response/                  # [EXISTING] Response tests
│   └── transformer_test.clj
├── mutation/                  # [NEW] Mutation tests
│   ├── parser_test.clj       # [NEW] Mutation parser tests
│   ├── lacinia_test.clj      # [NEW] Mutation schema generation tests
│   ├── transaction_test.clj  # [NEW] Transaction conversion tests
│   └── executor_test.clj     # [NEW] Mutation execution tests
└── integration/               # [EXISTING] Integration tests
    ├── schema_parsing_test.clj      # [EXISTING]
    ├── complete_workflow_test.clj   # [EXISTING] - will extend for mutations
    └── mutation_workflow_test.clj   # [NEW] End-to-end mutation tests
```

**Structure Decision**: Single library project following existing Ringline structure. New mutation support added as parallel namespace to existing query/response namespaces. This maintains consistency with the established architecture and allows mutation functionality to be independently developed and tested while integrating seamlessly with existing query capabilities.

## Complexity Tracking

**Status**: No constitutional violations - this section is empty.

All constitutional principles are followed without exceptions. The implementation uses simple, data-driven design with pure functions, follows TDD methodology, and maintains namespace-first architecture consistent with the existing codebase.

## Phase Completion Status

### Phase 0: Outline & Research ✅ COMPLETE

**Deliverable**: [research.md](research.md)

**Key Decisions**:
- Use `:ringline/mutations` property for declarative mutation definitions
- Generate standard CRUD mutations following GraphQL best practices
- Two-phase validation: Malli (pre-transaction) + Datomic (transaction-time)
- Support relationship references by ID, defer nested creates
- Use standard Datomic transaction patterns with tempids and lookup refs

**Status**: All research questions resolved. No NEEDS CLARIFICATION markers remain.

### Phase 1: Design & Contracts ✅ COMPLETE

**Deliverables**:
- [data-model.md](data-model.md) - 3 core entities with Malli schemas
- [contracts/mutation-parser.edn](contracts/mutation-parser.edn) - Parser API contract
- [contracts/lacinia-generator.edn](contracts/lacinia-generator.edn) - Schema generator contract
- [contracts/transaction-converter.edn](contracts/transaction-converter.edn) - Transaction converter contract
- [contracts/mutation-executor.edn](contracts/mutation-executor.edn) - Executor contract
- [quickstart.md](quickstart.md) - Developer quick start guide
- Agent context updated via `.specify/scripts/bash/update-agent-context.sh auggie`

**Constitution Re-Check**: ✅ **ALL GATES STILL PASS**

All design decisions align with constitutional principles:
- 4 new namespaces, each with single responsibility (Namespace-First)
- All entities are immutable maps (Data-Driven Design)
- Comprehensive test requirements in contracts (Test-First)
- Functions designed for REPL interaction (REPL-Driven)
- Malli validation at all boundaries (Schema Validation)
- Simple CRUD operations, no premature abstraction (Simplicity & Immutability)

### Phase 2: Task Breakdown - NOT STARTED

**Note**: Phase 2 is handled by the `/speckit.tasks` command, not `/speckit.plan`.

The planning phase ends here. Next step: Run `/speckit.tasks` to generate detailed implementation tasks.

## Summary

**Planning Status**: ✅ **COMPLETE**

All planning phases (Phase 0 and Phase 1) are complete. The implementation plan is ready for task breakdown and execution.

**Generated Artifacts**:
- ✅ plan.md (this file)
- ✅ research.md (6 research questions resolved)
- ✅ data-model.md (3 entities defined)
- ✅ contracts/ (4 API contracts)
- ✅ quickstart.md (developer guide)
- ✅ Agent context updated

**Next Steps**:
1. Run `/speckit.tasks` to generate detailed implementation tasks
2. Follow TDD workflow: Write tests → Implement → Verify
3. Implement in priority order: P1 (parser) → P2 (lacinia) → P3 (transaction) → P4 (executor)
4. Run tests continuously with Kaocha
5. Update README.md with mutation examples after implementation
