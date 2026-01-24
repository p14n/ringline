# Tasks: GraphQL Mutations

**Input**: Design documents from `/specs/002-graphql-mutations/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: This feature follows strict TDD methodology per Ringline Constitution. All test tasks MUST be completed and verified to FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- Single Clojure project: `src/ringline/`, `test/ringline/` at repository root
- All paths relative to `/Users/dean.chapman/dev/experiment/ringline`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and mutation namespace structure

- [x] T001 Create mutation namespace directories: `src/ringline/mutation/` and `test/ringline/mutation/`
- [x] T002 [P] Add `:ringline/mutations` property definition to `src/ringline/schema/properties.clj`
- [x] T003 [P] Extend test fixtures with mutation examples in `test/ringline/fixtures.clj`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data types that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Define MutationDefinition, MutationInput, MutationResult Malli schemas in `src/ringline/mutation/types.clj`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Define Mutation Operations in Malli (Priority: P1) 🎯 MVP

**Goal**: Parse mutation definitions from Malli schemas with `:ringline/mutations` property

**Independent Test**: Define a Malli schema with `:ringline/mutations #{:create :update :delete}`, verify framework correctly parses mutation definitions and derives input schemas

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T005 [P] [US1] Test parsing schema with all mutation types in `test/ringline/mutation/parser_test.clj`
- [x] T006 [P] [US1] Test parsing schema with subset of mutations in `test/ringline/mutation/parser_test.clj`
- [x] T007 [P] [US1] Test parsing schema with no mutations property in `test/ringline/mutation/parser_test.clj`
- [x] T008 [P] [US1] Test deriving create input schema (required fields only) in `test/ringline/mutation/parser_test.clj`
- [x] T009 [P] [US1] Test deriving update input schema (all fields optional) in `test/ringline/mutation/parser_test.clj`
- [x] T010 [P] [US1] Test deriving delete input schema (ID only) in `test/ringline/mutation/parser_test.clj`
- [x] T011 [P] [US1] Test handling invalid schema format in `test/ringline/mutation/parser_test.clj`
- [x] T012 [P] [US1] Test handling invalid operation type in `test/ringline/mutation/parser_test.clj`

### Implementation for User Story 1

- [x] T013 [US1] Implement `get-mutation-property` function in `src/ringline/mutation/parser.clj`
- [x] T014 [US1] Implement `derive-input-schema` function in `src/ringline/mutation/parser.clj`
- [x] T015 [US1] Implement `parse-mutations` function in `src/ringline/mutation/parser.clj`
- [x] T016 [US1] Add rich comment block with REPL examples to `src/ringline/mutation/parser.clj`
- [x] T017 [US1] Verify all User Story 1 tests pass with `bin/kaocha --focus ringline.mutation.parser-test`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Generate Lacinia Mutation Schema (Priority: P2)

**Goal**: Auto-generate Lacinia GraphQL mutation definitions from parsed mutation schemas

**Independent Test**: Provide a Malli schema with mutation definitions, verify generated Lacinia schema contains correct mutation fields, input objects, and return types

### Tests for User Story 2 ⚠️

- [x] T018 [P] [US2] Test generating mutations for entity with all operations in `test/ringline/mutation/lacinia_test.clj`
- [x] T019 [P] [US2] Test generating mutations for entity with subset of operations in `test/ringline/mutation/lacinia_test.clj`
- [x] T020 [P] [US2] Test mutation naming conventions (camelCase) in `test/ringline/mutation/lacinia_test.clj`
- [x] T021 [P] [US2] Test input type naming conventions (PascalCase + Input suffix) in `test/ringline/mutation/lacinia_test.clj`
- [x] T022 [P] [US2] Test create mutation with required fields in `test/ringline/mutation/lacinia_test.clj`
- [x] T023 [P] [US2] Test update mutation with optional fields in `test/ringline/mutation/lacinia_test.clj`
- [x] T024 [P] [US2] Test delete mutation with ID argument in `test/ringline/mutation/lacinia_test.clj`
- [x] T025 [P] [US2] Test input object generation from Malli schema in `test/ringline/mutation/lacinia_test.clj`
- [x] T026 [P] [US2] Test type mapping (Malli → GraphQL) in `test/ringline/mutation/lacinia_test.clj`
- [x] T027 [P] [US2] Test handling multiple entities in `test/ringline/mutation/lacinia_test.clj`
- [x] T028 [P] [US2] Test merging with existing Lacinia schema in `test/ringline/mutation/lacinia_test.clj`

### Implementation for User Story 2

- [x] T029 [US2] Implement `mutation-name` function in `src/ringline/mutation/lacinia.clj`
- [x] T030 [US2] Implement `input-type-name` function in `src/ringline/mutation/lacinia.clj`
- [x] T031 [US2] Implement `generate-input-object` function in `src/ringline/mutation/lacinia.clj`
- [x] T032 [US2] Implement `generate-mutation-field` function in `src/ringline/mutation/lacinia.clj`
- [x] T033 [US2] Implement `generate-mutation-schemas` function in `src/ringline/mutation/lacinia.clj`
- [x] T034 [US2] Add rich comment block with REPL examples to `src/ringline/mutation/lacinia.clj`
- [x] T035 [US2] Verify all User Story 2 tests pass with `bin/kaocha --focus ringline.mutation.lacinia-test`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Convert Mutation Inputs to Datomic Transactions (Priority: P3)

**Goal**: Convert GraphQL mutation inputs to Datomic transaction data with proper attribute mapping

**Independent Test**: Provide a GraphQL mutation input, verify generated Datomic transaction correctly represents create/update/delete with proper attribute names and values

### Tests for User Story 3 ⚠️

- [x] T036 [P] [US3] Test create transaction with all fields in `test/ringline/mutation/transaction_test.clj`
- [x] T037 [P] [US3] Test create transaction generates new UUID for ID in `test/ringline/mutation/transaction_test.clj`
- [x] T038 [P] [US3] Test create transaction uses tempid in `test/ringline/mutation/transaction_test.clj`
- [x] T039 [P] [US3] Test update transaction with partial data in `test/ringline/mutation/transaction_test.clj`
- [x] T040 [P] [US3] Test update transaction uses lookup ref in `test/ringline/mutation/transaction_test.clj`
- [x] T041 [P] [US3] Test delete transaction with entity ID in `test/ringline/mutation/transaction_test.clj`
- [x] T042 [P] [US3] Test attribute namespacing in `test/ringline/mutation/transaction_test.clj`
- [x] T043 [P] [US3] Test handling relationship fields (ref-to) in `test/ringline/mutation/transaction_test.clj`
- [x] T044 [P] [US3] Test handling vector fields (cardinality many) in `test/ringline/mutation/transaction_test.clj`
- [x] T045 [P] [US3] Test handling missing required fields (should error) in `test/ringline/mutation/transaction_test.clj`
- [x] T046 [P] [US3] Test handling invalid entity ID format in `test/ringline/mutation/transaction_test.clj`

### Implementation for User Story 3

- [x] T047 [US3] Implement `namespace-attributes` function in `src/ringline/mutation/transaction.clj`
- [x] T048 [US3] Implement `build-create-transaction` function in `src/ringline/mutation/transaction.clj`
- [x] T049 [US3] Implement `build-update-transaction` function in `src/ringline/mutation/transaction.clj`
- [x] T050 [US3] Implement `build-delete-transaction` function in `src/ringline/mutation/transaction.clj`
- [x] T051 [US3] Implement `input->transaction` function in `src/ringline/mutation/transaction.clj`
- [x] T052 [US3] Add rich comment block with REPL examples to `src/ringline/mutation/transaction.clj`
- [x] T053 [US3] Verify all User Story 3 tests pass with `bin/kaocha --focus ringline.mutation.transaction-test`

**Checkpoint**: At this point, User Stories 1, 2, AND 3 should all work independently

---

## Phase 6: User Story 4 - Execute Mutations and Return Results (Priority: P4)

**Goal**: Execute mutations through GraphQL API with validation, error handling, and response formatting

**Independent Test**: Execute a mutation operation, verify response contains mutated entity data, proper error messages for validation failures, and correct status indicators

### Tests for User Story 4 ⚠️

- [x] T054 [P] [US4] Test successful create mutation in `test/ringline/mutation/executor_test.clj`
- [x] T055 [P] [US4] Test successful update mutation in `test/ringline/mutation/executor_test.clj`
- [x] T056 [P] [US4] Test successful delete mutation in `test/ringline/mutation/executor_test.clj`
- [x] T057 [P] [US4] Test validation error handling in `test/ringline/mutation/executor_test.clj`
- [x] T058 [P] [US4] Test transaction error handling (constraint violation) in `test/ringline/mutation/executor_test.clj`
- [x] T059 [P] [US4] Test entity not found error (update/delete) in `test/ringline/mutation/executor_test.clj`
- [x] T060 [P] [US4] Test result formatting for success in `test/ringline/mutation/executor_test.clj`
- [x] T061 [P] [US4] Test result formatting for errors in `test/ringline/mutation/executor_test.clj`
- [x] T062 [P] [US4] Test tempid resolution for creates in `test/ringline/mutation/executor_test.clj`
- [x] T063 [P] [US4] Test timestamp generation in `test/ringline/mutation/executor_test.clj`
- [x] T064 [P] [US4] Test integration with response transformer in `test/ringline/mutation/executor_test.clj`

### Implementation for User Story 4

- [x] T065 [US4] Implement `validate-input` function in `src/ringline/mutation/executor.clj`
- [x] T066 [US4] Implement `execute-transaction` function in `src/ringline/mutation/executor.clj`
- [x] T067 [US4] Implement `resolve-created-entity` function in `src/ringline/mutation/executor.clj`
- [x] T068 [US4] Implement `format-success-result` function in `src/ringline/mutation/executor.clj`
- [x] T069 [US4] Implement `format-error-result` function in `src/ringline/mutation/executor.clj`
- [x] T070 [US4] Implement `execute-mutation` function in `src/ringline/mutation/executor.clj`
- [x] T071 [US4] Add rich comment block with REPL examples to `src/ringline/mutation/executor.clj`
- [x] T072 [US4] Verify all User Story 4 tests pass with `bin/kaocha --focus ringline.mutation.executor-test`

### Integration Test for User Story 4

- [x] T073 [US4] Create end-to-end mutation workflow test in `test/ringline/integration/mutation_workflow_test.clj`
- [x] T074 [US4] Verify integration test passes with `bin/kaocha --focus ringline.integration.mutation-workflow-test`

**Checkpoint**: All user stories should now be independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Integration with existing framework and documentation

- [x] T075 [P] Add `create-mutation-resolver` function to `src/ringline/core.clj`
- [x] T076 [P] Add `attach-mutation-resolvers` function to `src/ringline/core.clj`
- [x] T077 Update `init-framework` function to include mutation processing in `src/ringline/core.clj`
- [x] T078 [P] Extend `test/ringline/integration/complete_workflow_test.clj` with mutation scenarios
- [ ] T079 [P] Update README.md with mutation examples and usage guide
- [x] T080 Run full test suite with `bin/kaocha` and verify all tests pass
- [ ] T081 Validate quickstart.md examples work end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3 → P4)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Uses US1 parser but independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Uses US1 parser but independently testable
- **User Story 4 (P4)**: Can start after Foundational (Phase 2) - Integrates US1-3 but independently testable

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD mandate)
- All tests for a story can run in parallel (marked [P])
- Implementation tasks run sequentially (build helper functions first, then main functions)
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T002, T003)
- All tests within a user story marked [P] can run in parallel
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- Polish tasks marked [P] can run in parallel (T075, T076, T078, T079)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task T005: "Test parsing schema with all mutation types"
Task T006: "Test parsing schema with subset of mutations"
Task T007: "Test parsing schema with no mutations property"
Task T008: "Test deriving create input schema"
Task T009: "Test deriving update input schema"
Task T010: "Test deriving delete input schema"
Task T011: "Test handling invalid schema format"
Task T012: "Test handling invalid operation type"

# All 8 test tasks can be written in parallel (different test cases in same file)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004) - CRITICAL
3. Complete Phase 3: User Story 1 (T005-T017)
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Can demonstrate mutation parsing capability

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Mutation parsing works (MVP!)
3. Add User Story 2 → Test independently → GraphQL schema generation works
4. Add User Story 3 → Test independently → Transaction conversion works
5. Add User Story 4 → Test independently → Full mutation execution works
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together (T001-T004)
2. Once Foundational is done:
   - Developer A: User Story 1 (T005-T017)
   - Developer B: User Story 2 (T018-T035)
   - Developer C: User Story 3 (T036-T053)
   - Developer D: User Story 4 (T054-T074)
3. Stories complete and integrate independently
4. Team completes Polish together (T075-T081)

---

## Notes

- **TDD Mandate**: All tests MUST fail before implementation (constitutional requirement)
- **[P] tasks**: Different files or independent test cases, no dependencies
- **[Story] label**: Maps task to specific user story for traceability
- **Each user story**: Independently completable and testable
- **Verify tests fail**: Run `bin/kaocha --focus <namespace>` before implementing
- **Commit strategy**: Commit after each task or logical group
- **Checkpoints**: Stop at any checkpoint to validate story independently
- **File paths**: All paths are exact and absolute from repository root
- **REPL workflow**: Use rich comment blocks for interactive development
- **Total tasks**: 81 tasks across 7 phases

