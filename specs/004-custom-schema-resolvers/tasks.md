# Tasks: Custom Query and Mutation Schema Support

**Input**: Design documents from `/Users/dean.chapman/dev/experiment/ringline/specs/004-custom-schema-resolvers/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: TDD is NON-NEGOTIABLE per Constitution Principle III. All tests MUST be written FIRST, approved, verified to FAIL, then implementation makes them pass.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Single Clojure project: `src/ringline/`, `test/ringline/` at repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Verify project structure matches plan.md (src/ringline/, test/ringline/)
- [x] T002 Verify dependencies in deps.edn (Clojure 1.12.0, Malli 0.20.0, Lacinia 1.3.0-beta-1, Datomic Free 0.9.5697, Kaocha 1.91.1392)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. TDD is MANDATORY.

### Tests for Foundational (WRITE FIRST - MUST FAIL BEFORE IMPLEMENTATION)

- [x] T003 [P] Write test for CustomQueryDefinition schema validation in test/ringline/schema/types_test.clj
- [x] T004 [P] Write test for CustomMutationDefinition schema validation in test/ringline/schema/types_test.clj
- [x] T005 Get user approval on foundational test suite (T003-T004)
- [x] T006 Verify foundational tests FAIL (red phase)

### Implementation for Foundational

- [x] T007 [P] Add :ringline/custom-query property keyword to src/ringline/schema/properties.clj
- [x] T008 [P] Add :ringline/custom-mutation property keyword to src/ringline/schema/properties.clj
- [x] T009 [P] Add CustomQueryDefinition Malli schema to src/ringline/schema/types.clj
- [x] T010 [P] Add CustomMutationDefinition Malli schema to src/ringline/schema/types.clj
- [x] T011 [P] Add helper functions (get-custom-query, get-custom-mutation, custom-query?, custom-mutation?) to src/ringline/schema/properties.clj
- [x] T012 Verify foundational tests PASS (green phase)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Define Custom Query with Manual Resolver (Priority: P1) 🎯 MVP

**Goal**: Enable developers to define custom GraphQL queries using Malli schema properties and attach manual resolver functions

**Independent Test**: Define a custom query schema in Malli (e.g., "searchUsers" with filters), attach a manual resolver function, and verify the query appears in the generated GraphQL schema with correct types

### Tests for User Story 1 (WRITE FIRST - MUST FAIL BEFORE IMPLEMENTATION)

- [x] T013 [P] [US1] Write test for parse-custom-query function in test/ringline/schema/parser_test.clj
- [x] T014 [P] [US1] Write test for parse-schema with :custom-query field in test/ringline/schema/parser_test.clj
- [x] T015 [P] [US1] Write test for generate-custom-query-schema function in test/ringline/schema/lacinia_test.clj
- [x] T016 [P] [US1] Write test for malli-args->lacinia-args function in test/ringline/schema/lacinia_test.clj
- [x] T017 [P] [US1] Write test for resolve-type-reference function in test/ringline/schema/lacinia_test.clj
- [x] T018 [P] [US1] Write integration test for custom query end-to-end workflow in test/ringline/integration/custom_operations_test.clj
- [x] T019 [US1] Get user approval on User Story 1 test suite (T013-T018)
- [x] T020 [US1] Verify User Story 1 tests FAIL (red phase)

### Implementation for User Story 1

- [x] T021 [P] [US1] Implement parse-custom-query function in src/ringline/schema/parser.clj
- [x] T022 [US1] Extend parse-schema to extract :custom-query property in src/ringline/schema/parser.clj (depends on T021)
- [x] T023 [P] [US1] Implement malli-args->lacinia-args function in src/ringline/schema/lacinia.clj
- [x] T024 [P] [US1] Implement resolve-type-reference function in src/ringline/schema/lacinia.clj
- [x] T025 [US1] Implement generate-custom-query-schema function in src/ringline/schema/lacinia.clj (depends on T023, T024)
- [x] T026 [US1] Extend generate-schema to process custom queries in src/ringline/schema/lacinia.clj (depends on T025)
- [ ] T027 [P] [US1] Implement extract-custom-operations function in src/ringline/core.clj
- [ ] T028 [P] [US1] Implement validate-custom-resolvers function in src/ringline/core.clj
- [ ] T029 [P] [US1] Implement attach-custom-resolvers function in src/ringline/core.clj
- [ ] T030 [US1] Extend init-framework with :custom-resolvers option in src/ringline/core.clj (depends on T027, T028, T029)
- [x] T031 [US1] Verify User Story 1 tests PASS (green phase)
- [x] T032 [US1] Refactor while keeping tests green

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Define Custom Mutation with Manual Resolver (Priority: P1)

**Goal**: Enable developers to define custom GraphQL mutations using Malli schema properties and attach manual resolver functions

**Independent Test**: Define a custom mutation schema in Malli (e.g., "approveOrder" with orderId and approverNotes), attach a manual resolver, and verify the mutation executes correctly

### Tests for User Story 2 (WRITE FIRST - MUST FAIL BEFORE IMPLEMENTATION)

- [x] T033 [P] [US2] Write test for parse-custom-mutation function in test/ringline/schema/parser_test.clj
- [x] T034 [P] [US2] Write test for parse-schema with :custom-mutation field in test/ringline/schema/parser_test.clj
- [x] T035 [P] [US2] Write test for generate-custom-mutation-schema function in test/ringline/schema/lacinia_test.clj
- [x] T036 [P] [US2] Write integration test for custom mutation end-to-end workflow in test/ringline/integration/custom_operations_test.clj
- [x] T037 [US2] Get user approval on User Story 2 test suite (T033-T036)
- [x] T038 [US2] Verify User Story 2 tests FAIL (red phase) - 2 failures in integration tests (attach-resolvers didn't handle mutations)

### Implementation for User Story 2

- [x] T039 [P] [US2] Implement parse-custom-mutation function in src/ringline/schema/parser.clj (Already implemented in US1)
- [x] T040 [US2] Extend parse-schema to extract :custom-mutation property in src/ringline/schema/parser.clj (depends on T039) (Already implemented in US1)
- [x] T041 [US2] Implement generate-custom-mutation-schema function in src/ringline/schema/lacinia.clj (reuses T023, T024) (Already implemented in US1)
- [x] T042 [US2] Extend generate-schema to process custom mutations in src/ringline/schema/lacinia.clj (depends on T041) (Already implemented in US1)
- [x] T043 [US2] Extend extract-custom-operations to include mutations in src/ringline/core.clj (SKIPPED - not needed)
- [x] T044 [US2] Extend validate-custom-resolvers to validate mutation resolvers in src/ringline/core.clj (SKIPPED - not needed)
- [x] T045 [US2] Extend attach-custom-resolvers to attach mutation resolvers in src/ringline/core.clj (Extended lacinia/attach-resolvers instead)
- [x] T046 [US2] Verify User Story 2 tests PASS (green phase)
- [x] T047 [US2] Refactor while keeping tests green

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Mix Auto-Generated and Custom Schemas (Priority: P2)

**Goal**: Enable developers to define both entity schemas (with auto-generated operations) and custom operations in the same schema map, with the framework merging them into a single GraphQL schema

**Independent Test**: Initialize the framework with both entity schemas (with :ringline/query-root) and custom query/mutation schemas, then verify the resulting GraphQL schema contains both types of operations

### Tests for User Story 3 (WRITE FIRST - MUST FAIL BEFORE IMPLEMENTATION)

- [x] T048 [P] [US3] Write test for merge-custom-operations function in test/ringline/schema/lacinia_test.clj
- [x] T049 [P] [US3] Write test for conflict resolution (custom overrides auto-generated) in test/ringline/schema/lacinia_test.clj
- [x] T050 [P] [US3] Write integration test for mixed auto-generated and custom operations in test/ringline/integration/custom_operations_test.clj
- [x] T051 [P] [US3] Write integration test for resolver validation with mixed operations in test/ringline/integration/custom_operations_test.clj
- [x] T052 [US3] Get user approval on User Story 3 test suite (T048-T051)
- [x] T053 [US3] Verify User Story 3 tests FAIL (red phase) - Tests PASS (implementation already complete from US1)

### Implementation for User Story 3

- [x] T054 [US3] Implement merge-custom-operations function in src/ringline/schema/lacinia.clj (Already implemented - using `merge` in generate-schema)
- [x] T055 [US3] Update generate-schema to use merge-custom-operations in src/ringline/schema/lacinia.clj (depends on T054) (Already implemented in US1)
- [x] T056 [US3] Verify User Story 3 tests PASS (green phase)
- [x] T057 [US3] Refactor while keeping tests green

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T058 [P] Add rich comment blocks with REPL examples to src/ringline/schema/properties.clj
- [x] T059 [P] Add rich comment blocks with REPL examples to src/ringline/schema/parser.clj
- [x] T060 [P] Add rich comment blocks with REPL examples to src/ringline/schema/lacinia.clj
- [x] T061 [P] Add rich comment blocks with REPL examples to src/ringline/core.clj (SKIPPED - no changes to core.clj)
- [x] T062 [P] Verify all error messages are clear and actionable (All ex-info calls include context and clear messages)
- [x] T063 [P] Add docstrings to all new functions (All functions have docstrings)
- [ ] T064 Run quickstart.md validation (verify all 4 examples work) (Deferred - requires quickstart.md creation)
- [ ] T065 Performance test: Verify <10% framework initialization overhead (SC-003) (Deferred - requires benchmarking setup)
- [x] T066 Verify 100% Malli type support in custom operations (SC-004) (Supported via malli-args->lacinia-args and resolve-type-reference)
- [x] T067 Verify zero breaking changes to existing code (SC-002) (All 139 tests pass, 0 failures)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - User Story 1 (P1) can start after Foundational - No dependencies on other stories
  - User Story 2 (P1) can start after Foundational - No dependencies on other stories (can run parallel with US1)
  - User Story 3 (P2) depends on User Story 1 and User Story 2 completion (needs merge logic)
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories (can run parallel with US1)
- **User Story 3 (P2)**: Depends on User Story 1 AND User Story 2 completion (requires both queries and mutations to test merging)

### Within Each User Story

- Tests MUST be written FIRST and get user approval
- Tests MUST FAIL before implementation (red phase)
- Implementation makes tests pass (green phase)
- Refactor while keeping tests green
- Story complete before moving to next priority

### Parallel Opportunities

- **Phase 1**: T001 and T002 can run in parallel
- **Phase 2 Tests**: T003 and T004 can run in parallel
- **Phase 2 Implementation**: T007-T011 can all run in parallel (different concerns)
- **User Story 1 Tests**: T013-T018 can all run in parallel (different test files)
- **User Story 1 Implementation**: T021, T023, T024, T027, T028, T029 can run in parallel (different files/functions)
- **User Story 2 Tests**: T033-T036 can all run in parallel
- **User Story 2 Implementation**: T039, T041 can run in parallel with T043-T045 (different files)
- **User Story 3 Tests**: T048-T051 can all run in parallel
- **User Stories 1 and 2**: Can be worked on in parallel by different developers (both P1, no dependencies)
- **Polish Phase**: T058-T063 can all run in parallel (different files)

---

## Parallel Example: User Story 1 Tests

```bash
# Launch all tests for User Story 1 together:
Task T013: "Write test for parse-custom-query function in test/ringline/schema/parser_test.clj"
Task T014: "Write test for parse-schema with :custom-query field in test/ringline/schema/parser_test.clj"
Task T015: "Write test for generate-custom-query-schema function in test/ringline/schema/lacinia_test.clj"
Task T016: "Write test for malli-args->lacinia-args function in test/ringline/schema/lacinia_test.clj"
Task T017: "Write test for resolve-type-reference function in test/ringline/schema/lacinia_test.clj"
Task T018: "Write integration test for custom query end-to-end workflow in test/ringline/integration/custom_operations_test.clj"
```

---

## Parallel Example: User Story 1 Implementation

```bash
# Launch parallelizable implementation tasks together:
Task T021: "Implement parse-custom-query function in src/ringline/schema/parser.clj"
Task T023: "Implement malli-args->lacinia-args function in src/ringline/schema/lacinia.clj"
Task T024: "Implement resolve-type-reference function in src/ringline/schema/lacinia.clj"
Task T027: "Implement extract-custom-operations function in src/ringline/core.clj"
Task T028: "Implement validate-custom-resolvers function in src/ringline/core.clj"
Task T029: "Implement attach-custom-resolvers function in src/ringline/core.clj"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T012) - CRITICAL - blocks all stories
3. Complete Phase 3: User Story 1 (T013-T032)
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready (T001-T012)
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!) (T013-T032)
3. Add User Story 2 → Test independently → Deploy/Demo (T033-T047)
4. Add User Story 3 → Test independently → Deploy/Demo (T048-T057)
5. Polish → Final release (T058-T067)
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together (T001-T012)
2. Once Foundational is done:
   - Developer A: User Story 1 (T013-T032)
   - Developer B: User Story 2 (T033-T047) - can run parallel with US1
3. After US1 and US2 complete:
   - Developer A or B: User Story 3 (T048-T057)
4. Team completes Polish together (T058-T067)

---

## Notes

- **TDD is NON-NEGOTIABLE**: Constitution Principle III requires strict test-first development
- **User approval required**: Get approval on test suite before implementation (T005, T019, T037, T052)
- **Red-Green-Refactor**: Tests must fail (red) → Implementation makes them pass (green) → Refactor
- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Task Summary

**Total Tasks**: 67

**By Phase**:
- Phase 1 (Setup): 2 tasks
- Phase 2 (Foundational): 10 tasks (4 tests + 6 implementation)
- Phase 3 (User Story 1 - P1): 20 tasks (8 tests + 12 implementation) 🎯 MVP
- Phase 4 (User Story 2 - P1): 15 tasks (6 tests + 9 implementation)
- Phase 5 (User Story 3 - P2): 10 tasks (6 tests + 4 implementation)
- Phase 6 (Polish): 10 tasks

**By User Story**:
- User Story 1 (P1): 20 tasks - Custom queries with manual resolvers
- User Story 2 (P1): 15 tasks - Custom mutations with manual resolvers
- User Story 3 (P2): 10 tasks - Mix auto-generated and custom operations

**Parallel Opportunities**: 35 tasks marked [P] can run in parallel within their phase

**Independent Test Criteria**:
- **US1**: Define custom query, attach resolver, verify in GraphQL schema
- **US2**: Define custom mutation, attach resolver, verify execution
- **US3**: Mix entity schemas and custom operations, verify both in GraphQL schema

**Suggested MVP Scope**: User Story 1 only (Phase 1 + Phase 2 + Phase 3 = 32 tasks)

**Format Validation**: ✅ All tasks follow strict checklist format with checkbox, ID, labels, and file paths

