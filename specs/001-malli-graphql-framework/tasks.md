# Tasks: Malli-GraphQL Framework

**Input**: Design documents from `/specs/001-malli-graphql-framework/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: TDD is MANDATORY per constitution (Principle III). All tests MUST be written FIRST and FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single library project**: `src/ringline/`, `test/ringline/` at repository root
- Paths follow Clojure conventions with namespace directories

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create namespace directory structure: src/ringline/schema/, src/ringline/query/, src/ringline/response/
- [x] T002 Create test directory structure: test/ringline/schema/, test/ringline/query/, test/ringline/response/, test/ringline/integration/
- [x] T003 [P] Configure Kaocha test runner in tests.edn
- [x] T004 [P] Add Malli 0.20.0, Lacinia 1.3.0-beta-1, Datomic Free 0.9.5697 to deps.edn (if not present)
- [x] T005 [P] Setup REPL configuration with tools.namespace for safe reloading

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core utilities and type mappings that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T006 Create type mapping data structures in src/ringline/schema/types.clj (Malli→Datomic, Malli→GraphQL)
- [x] T007 [P] Create custom Malli property keywords in src/ringline/schema/properties.clj (:ringline/datomic-ns, :ringline/query-root, :ringline/searchable)
- [x] T008 [P] Create test fixtures with example Malli schemas in test/ringline/fixtures.clj (User, Post entities)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Define Data Model with Malli (Priority: P1) 🎯 MVP

**Goal**: Parse Malli schemas and extract entity metadata, fields, relationships, and custom properties

**Independent Test**: Define Malli schemas with entities and relationships, verify framework correctly parses structure, extracts metadata, and identifies relationships

### Tests for User Story 1 (TDD - Write FIRST, ensure FAIL)

- [x] T009 [P] [US1] Contract test for parse-schema function in test/ringline/schema/parser_test.clj
- [x] T010 [P] [US1] Contract test for parse-schemas function in test/ringline/schema/parser_test.clj
- [x] T011 [P] [US1] Integration test for multi-entity parsing with relationships in test/ringline/integration/schema_parsing_test.clj

### Implementation for User Story 1

- [x] T012 [US1] Implement parse-schema function in src/ringline/schema/parser.clj (extract fields, types, properties)
- [x] T013 [US1] Implement field extraction logic in src/ringline/schema/parser.clj (handle Malli m/children)
- [x] T014 [US1] Implement property extraction logic in src/ringline/schema/parser.clj (handle Malli m/properties)
- [x] T015 [US1] Implement relationship detection in src/ringline/schema/parser.clj (identify :ref types, cardinality)
- [x] T016 [US1] Implement parse-schemas function in src/ringline/schema/parser.clj (handle multiple entities, resolve relationships)
- [x] T017 [US1] Add Malli validation for ParsedSchema output in src/ringline/schema/parser.clj
- [x] T018 [US1] Verify all tests pass for User Story 1

**Checkpoint**: At this point, User Story 1 should be fully functional - can parse Malli schemas and extract all metadata

---

## Phase 4: User Story 2 - Generate Datomic Schema (Priority: P2)

**Goal**: Automatically generate valid Datomic schema transactions from parsed Malli schemas with correct types, cardinality, and namespaces

**Independent Test**: Provide complete Malli data model, verify generated Datomic schema has correct attribute definitions, namespacing, cardinality, and valid types

### Tests for User Story 2 (TDD - Write FIRST, ensure FAIL)

- [x] T019 [P] [US2] Contract test for generate-schema function in test/ringline/schema/datomic_test.clj
- [x] T020 [P] [US2] Contract test for generate-schemas function in test/ringline/schema/datomic_test.clj
- [x] T021 [P] [US2] Contract test for schema->transaction function in test/ringline/schema/datomic_test.clj
- [x] T022 [P] [US2] Integration test for end-to-end Datomic schema generation in test/ringline/integration/schema_generation_test.clj

### Implementation for User Story 2

- [x] T023 [US2] Implement Malli to Datomic type mapping in src/ringline/schema/datomic.clj (use types.clj mappings)
- [x] T024 [US2] Implement cardinality detection in src/ringline/schema/datomic.clj (:one vs :many from Malli structure)
- [x] T025 [US2] Implement namespace application in src/ringline/schema/datomic.clj (use :ringline/datomic-ns property)
- [x] T026 [US2] Implement generate-schema function in src/ringline/schema/datomic.clj (ParsedSchema → DatomicSchema)
- [x] T027 [US2] Implement generate-schemas function in src/ringline/schema/datomic.clj (handle multiple entities)
- [x] T028 [US2] Implement schema->transaction function in src/ringline/schema/datomic.clj (DatomicSchema → transaction data)
- [x] T029 [US2] Add Malli validation for DatomicSchema output in src/ringline/schema/datomic.clj
- [x] T030 [US2] Verify all tests pass for User Story 2

**Checkpoint**: At this point, User Stories 1 AND 2 work independently - can generate Datomic schemas from Malli

---

## Phase 5: User Story 3 - Generate Lacinia GraphQL Schema (Priority: P3)

**Goal**: Automatically generate valid Lacinia GraphQL schema from parsed Malli schemas with object types, fields, relationships, and query roots

**Independent Test**: Provide Malli data model, verify generated Lacinia schema has correct GraphQL object types, field types, relationship definitions, and query roots

### Tests for User Story 3 (TDD - Write FIRST, ensure FAIL)

- [x] T031 [P] [US3] Contract test for generate-schema function in test/ringline/schema/lacinia_test.clj
- [x] T032 [P] [US3] Contract test for generate-schemas function in test/ringline/schema/lacinia_test.clj
- [x] T033 [P] [US3] Contract test for attach-resolvers function in test/ringline/schema/lacinia_test.clj
- [x] T034 [P] [US3] Integration test for end-to-end Lacinia schema generation in test/ringline/integration/schema_generation_test.clj

### Implementation for User Story 3

- [x] T035 [US3] Implement Malli to GraphQL type mapping in src/ringline/schema/lacinia.clj (use types.clj mappings)
- [x] T036 [US3] Implement GraphQL object type generation in src/ringline/schema/lacinia.clj (create :objects map)
- [x] T037 [US3] Implement query root detection in src/ringline/schema/lacinia.clj (use :ringline/query-root property)
- [x] T038 [US3] Implement searchable parameter detection in src/ringline/schema/lacinia.clj (use :ringline/searchable property)
- [x] T039 [US3] Implement query argument generation in src/ringline/schema/lacinia.clj (create :args for searchable fields)
- [x] T040 [US3] Implement relationship field generation in src/ringline/schema/lacinia.clj (GraphQL object references)
- [x] T041 [US3] Implement generate-schema function in src/ringline/schema/lacinia.clj (ParsedSchema → LaciniaSchema)
- [x] T042 [US3] Implement generate-schemas function in src/ringline/schema/lacinia.clj (merge multiple entities into single schema)
- [x] T043 [US3] Implement attach-resolvers function in src/ringline/schema/lacinia.clj (attach resolver functions to schema)
- [x] T044 [US3] Add Malli validation for LaciniaSchema output in src/ringline/schema/lacinia.clj
- [x] T045 [US3] Verify all tests pass for User Story 3

**Checkpoint**: At this point, User Stories 1, 2, AND 3 work independently - can generate both Datomic and Lacinia schemas

---

## Phase 6: User Story 4 - Convert GraphQL Queries to Datomic Pull (Priority: P4)

**Goal**: Convert incoming GraphQL queries to equivalent Datomic pull patterns with correct field selections, nested relationships, and filtering

**Independent Test**: Provide GraphQL query AST, verify generated Datomic pull pattern has requested fields, nested pulls for relationships, and query clauses for filtering

### Tests for User Story 4 (TDD - Write FIRST, ensure FAIL)

- [ ] T046 [P] [US4] Contract test for build-query-context function in test/ringline/query/converter_test.clj
- [ ] T047 [P] [US4] Contract test for graphql->pull function in test/ringline/query/converter_test.clj
- [ ] T048 [P] [US4] Contract test for pull-with-args function in test/ringline/query/converter_test.clj
- [ ] T049 [P] [US4] Integration test for end-to-end query conversion in test/ringline/integration/query_execution_test.clj

### Implementation for User Story 4

- [ ] T050 [US4] Implement build-query-context function in src/ringline/query/converter.clj (extract selections from Lacinia context)
- [ ] T051 [US4] Implement field selection extraction in src/ringline/query/converter.clj (parse GraphQL field selections)
- [ ] T052 [US4] Implement nested query detection in src/ringline/query/converter.clj (identify relationship traversals)
- [ ] T053 [US4] Implement pull pattern generation in src/ringline/query/converter.clj (convert selections to Datomic pull syntax)
- [ ] T054 [US4] Implement nested pull pattern generation in src/ringline/query/converter.clj (handle nested relationships)
- [ ] T055 [US4] Implement graphql->pull function in src/ringline/query/converter.clj (QueryContext → PullPattern)
- [ ] T056 [US4] Implement argument extraction in src/ringline/query/converter.clj (extract GraphQL query arguments)
- [ ] T057 [US4] Implement where clause generation in src/ringline/query/converter.clj (convert arguments to Datomic query clauses)
- [ ] T058 [US4] Implement pull-with-args function in src/ringline/query/converter.clj (combine pull pattern with where clauses)
- [ ] T059 [US4] Add Malli validation for PullPattern output in src/ringline/query/converter.clj
- [ ] T060 [US4] Verify all tests pass for User Story 4

**Checkpoint**: At this point, User Stories 1-4 work - can convert GraphQL queries to Datomic pull patterns

---

## Phase 7: User Story 5 - Convert Datomic Responses to Lacinia Format (Priority: P5)

**Goal**: Transform Datomic query results to Lacinia-compatible GraphQL response format with correct field names, resolved relationships, and type coercion

**Independent Test**: Provide Datomic entity results, verify converted output matches Lacinia format, resolves relationships, and coerces types correctly

### Tests for User Story 5 (TDD - Write FIRST, ensure FAIL)

- [ ] T061 [P] [US5] Contract test for datomic->graphql function in test/ringline/response/transformer_test.clj
- [ ] T062 [P] [US5] Contract test for entities->graphql function in test/ringline/response/transformer_test.clj
- [ ] T063 [P] [US5] Contract test for transform-with-selections function in test/ringline/response/transformer_test.clj
- [ ] T064 [P] [US5] Integration test for end-to-end response transformation in test/ringline/integration/query_execution_test.clj

### Implementation for User Story 5

- [ ] T065 [US5] Implement field name transformation in src/ringline/response/transformer.clj (Datomic keywords → GraphQL field names)
- [ ] T066 [US5] Implement type coercion in src/ringline/response/transformer.clj (Datomic types → GraphQL types)
- [ ] T067 [US5] Implement relationship resolution in src/ringline/response/transformer.clj (resolve :db/id refs to nested entities)
- [ ] T068 [US5] Implement null handling in src/ringline/response/transformer.clj (respect GraphQL schema nullability)
- [ ] T069 [US5] Implement datomic->graphql function in src/ringline/response/transformer.clj (single entity transformation)
- [ ] T070 [US5] Implement entities->graphql function in src/ringline/response/transformer.clj (list of entities transformation)
- [ ] T071 [US5] Implement transform-with-selections function in src/ringline/response/transformer.clj (filter by QueryContext selections)
- [ ] T072 [US5] Add Malli validation for transformed output in src/ringline/response/transformer.clj
- [ ] T073 [US5] Verify all tests pass for User Story 5

**Checkpoint**: All 5 user stories complete - full query execution flow works end-to-end

---

## Phase 8: Integration & Framework API (Cross-Story)

**Purpose**: Tie all user stories together with high-level API and integration points

### Tests for Integration (TDD - Write FIRST, ensure FAIL)

- [ ] T074 [P] Contract test for init-framework function in test/ringline/core_test.clj
- [ ] T075 [P] Contract test for create-resolver function in test/ringline/core_test.clj
- [ ] T076 Integration test for complete workflow (Malli → Datomic + Lacinia → Query → Response) in test/ringline/integration/complete_workflow_test.clj

### Implementation for Integration

- [ ] T077 Implement init-framework function in src/ringline/core.clj (orchestrate parse + generate Datomic + generate Lacinia)
- [ ] T078 Implement create-resolver function in src/ringline/core.clj (create Lacinia resolver using query converter + response transformer)
- [ ] T079 Add comprehensive error handling across all namespaces
- [ ] T080 Add logging for framework operations (schema generation, query conversion, response transformation)
- [ ] T081 Verify all integration tests pass

**Checkpoint**: Complete framework ready for use

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T082 [P] Add rich comment blocks with REPL examples to all namespaces
- [ ] T083 [P] Add docstrings to all public functions
- [ ] T084 [P] Validate quickstart.md examples work correctly
- [ ] T085 Code cleanup and refactoring for consistency
- [ ] T086 Performance optimization for schema generation (target: <1s for 50 entities)
- [ ] T087 Performance optimization for query conversion (target: <10ms per query)
- [ ] T088 [P] Add edge case handling (unsupported types, circular relationships, missing fields)
- [ ] T089 Security review for query injection vulnerabilities
- [ ] T090 Run full test suite with Kaocha and verify 100% pass rate

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational - No dependencies on other stories
- **User Story 2 (Phase 4)**: Depends on Foundational AND User Story 1 (needs ParsedSchema)
- **User Story 3 (Phase 5)**: Depends on Foundational AND User Story 1 (needs ParsedSchema)
- **User Story 4 (Phase 6)**: Depends on User Story 3 (needs LaciniaSchema for query context)
- **User Story 5 (Phase 7)**: Depends on User Story 1 (needs ParsedSchema for transformation rules)
- **Integration (Phase 8)**: Depends on ALL user stories (1-5) being complete
- **Polish (Phase 9)**: Depends on Integration completion

### User Story Dependencies

```
Foundational (Phase 2)
    ├── User Story 1 (P1) - Parse Malli Schemas
    │   ├── User Story 2 (P2) - Generate Datomic Schema
    │   ├── User Story 3 (P3) - Generate Lacinia Schema
    │   │   └── User Story 4 (P4) - Convert GraphQL to Datomic Pull
    │   └── User Story 5 (P5) - Convert Datomic to GraphQL Response
    └── Integration (Phase 8)
        └── Polish (Phase 9)
```

**Parallel Opportunities**:
- User Story 2 and User Story 3 can run in parallel (both depend only on US1)
- User Story 4 and User Story 5 can run in parallel (US4 depends on US3, US5 depends on US1)

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD mandatory)
- All tests for a story marked [P] can run in parallel
- Implementation tasks follow dependencies (e.g., type mapping before schema generation)
- Story complete and tests passing before moving to next priority

### Parallel Opportunities by Phase

**Phase 1 (Setup)**: Tasks T003, T004, T005 can run in parallel

**Phase 2 (Foundational)**: Tasks T007, T008 can run in parallel

**Phase 3 (US1 Tests)**: Tasks T009, T010, T011 can run in parallel

**Phase 4 (US2 Tests)**: Tasks T019, T020, T021, T022 can run in parallel

**Phase 5 (US3 Tests)**: Tasks T031, T032, T033, T034 can run in parallel

**Phase 6 (US4 Tests)**: Tasks T046, T047, T048, T049 can run in parallel

**Phase 7 (US5 Tests)**: Tasks T061, T062, T063, T064 can run in parallel

**Phase 8 (Integration Tests)**: Tasks T074, T075 can run in parallel

**Phase 9 (Polish)**: Tasks T082, T083, T084, T088 can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (TDD - write first):
Task: "Contract test for parse-schema function in test/ringline/schema/parser_test.clj"
Task: "Contract test for parse-schemas function in test/ringline/schema/parser_test.clj"
Task: "Integration test for multi-entity parsing in test/ringline/integration/schema_parsing_test.clj"

# After tests written and failing, implement in sequence:
# T012 → T013 → T014 → T015 → T016 → T017 → T018
```

## Parallel Example: User Story 2 & 3

```bash
# After User Story 1 complete, these can run in parallel:

# Team Member A works on User Story 2 (Datomic):
Task: "Write tests for Datomic schema generation (T019-T022)"
Task: "Implement Datomic schema generation (T023-T030)"

# Team Member B works on User Story 3 (Lacinia) simultaneously:
Task: "Write tests for Lacinia schema generation (T031-T034)"
Task: "Implement Lacinia schema generation (T035-T045)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T005)
2. Complete Phase 2: Foundational (T006-T008) - CRITICAL
3. Complete Phase 3: User Story 1 (T009-T018)
4. **STOP and VALIDATE**: Test Malli schema parsing independently
5. Can now parse Malli schemas and extract metadata - minimal viable framework

### Incremental Delivery (Recommended)

1. **Foundation**: Setup + Foundational (T001-T008) → Foundation ready
2. **Increment 1**: Add User Story 1 (T009-T018) → Can parse Malli schemas ✅
3. **Increment 2**: Add User Story 2 (T019-T030) → Can generate Datomic schemas ✅
4. **Increment 3**: Add User Story 3 (T031-T045) → Can generate Lacinia schemas ✅
5. **Increment 4**: Add User Story 4 (T046-T060) → Can convert GraphQL queries ✅
6. **Increment 5**: Add User Story 5 (T061-T073) → Complete query execution flow ✅
7. **Integration**: Add Phase 8 (T074-T081) → High-level API ready ✅
8. **Polish**: Add Phase 9 (T082-T090) → Production-ready framework ✅

Each increment adds value and can be tested independently.

### Parallel Team Strategy

With multiple developers:

1. **Together**: Complete Setup + Foundational (T001-T008)
2. **Once Foundational done**:
   - Developer A: User Story 1 (T009-T018) - MUST complete first
3. **After User Story 1 complete**:
   - Developer A: User Story 2 (T019-T030) - Datomic generation
   - Developer B: User Story 3 (T031-T045) - Lacinia generation (parallel!)
4. **After User Stories 2 & 3 complete**:
   - Developer A: User Story 4 (T046-T060) - Query conversion
   - Developer B: User Story 5 (T061-T073) - Response transformation (parallel!)
5. **Together**: Integration (T074-T081) and Polish (T082-T090)

---

## Notes

- **[P] tasks** = different files, no dependencies, can run in parallel
- **[Story] label** maps task to specific user story for traceability
- **TDD is mandatory** per constitution - all tests MUST be written first and fail before implementation
- Each user story should be independently completable and testable
- Verify tests fail (Red), implement (Green), refactor (Refactor)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Use REPL for interactive development and testing
- Avoid: vague tasks, same file conflicts, breaking constitutional principles

---

## Task Count Summary

- **Total Tasks**: 90
- **Phase 1 (Setup)**: 5 tasks
- **Phase 2 (Foundational)**: 3 tasks
- **Phase 3 (User Story 1)**: 10 tasks (3 tests + 7 implementation)
- **Phase 4 (User Story 2)**: 12 tasks (4 tests + 8 implementation)
- **Phase 5 (User Story 3)**: 15 tasks (4 tests + 11 implementation)
- **Phase 6 (User Story 4)**: 15 tasks (4 tests + 11 implementation)
- **Phase 7 (User Story 5)**: 13 tasks (4 tests + 9 implementation)
- **Phase 8 (Integration)**: 8 tasks (3 tests + 5 implementation)
- **Phase 9 (Polish)**: 9 tasks

**Parallel Opportunities**: 28 tasks marked [P] can run in parallel within their phases

**Independent Test Criteria**:
- **US1**: Can parse Malli schemas and extract all metadata
- **US2**: Can generate valid Datomic schemas from Malli
- **US3**: Can generate valid Lacinia schemas from Malli
- **US4**: Can convert GraphQL queries to Datomic pull patterns
- **US5**: Can transform Datomic results to GraphQL format

**Suggested MVP Scope**: Phase 1 + Phase 2 + Phase 3 (User Story 1 only) = 18 tasks

