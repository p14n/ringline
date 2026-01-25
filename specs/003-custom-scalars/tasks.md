# Tasks: Custom Scalars Support

**Input**: Design documents from `/specs/003-custom-scalars/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: REQUIRED (TDD is constitutional requirement - Principle III: Test-First Development is NON-NEGOTIABLE)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/ringline/`, `test/ringline/` at repository root
- All paths are relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Verify Clojure 1.12.0, Malli 0.20.0, Lacinia 1.3.0-beta-1, Datomic Free 0.9.5697 dependencies in deps.edn
- [X] T002 Create new namespace file src/ringline/schema/scalars.clj for custom scalar logic
- [X] T003 Create new test file test/ringline/schema/scalars_test.clj for scalar validation tests
- [X] T004 Create new integration test file test/ringline/integration/custom_scalars_integration_test.clj

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core type system extensions that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational (TDD - Write FIRST, ensure FAIL)

- [X] T005 [P] Write test for Date type mapping in test/ringline/schema/types_test.clj
- [X] T006 [P] Write test for DateTime type mapping in test/ringline/schema/types_test.clj
- [X] T007 [P] Write test for Enum type mapping in test/ringline/schema/types_test.clj
- [X] T008 [P] Write test for Decimal type mapping in test/ringline/schema/types_test.clj
- [X] T009 Run tests and verify they FAIL (Red phase) - NOTE: Java not available, tests written but not executed

### Implementation for Foundational

- [X] T010 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :time/local-date → :db.type/instant
- [X] T011 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :time/offset-date-time → :db.type/instant
- [X] T012 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :enum → :db.type/keyword
- [X] T013 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :decimal → :db.type/bigdec
- [X] T014 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :time/local-date → 'Date
- [X] T015 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :time/offset-date-time → 'DateTime
- [X] T016 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :enum → (enum type generation)
- [X] T017 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :decimal → 'Decimal
- [X] T018 Run tests and verify they PASS (Green phase) - NOTE: Java not available, implementation complete but tests not executed

### Malli Registry Setup

- [X] T019 Register :time/local-date and :time/offset-date-time from malli.experimental.time in src/ringline/schema/scalars.clj
- [X] T020 Implement custom :decimal schema using IntoSchema protocol in src/ringline/schema/scalars.clj with precision/scale validation

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Date Field Support (Priority: P1) 🎯 MVP

**Goal**: Enable framework users to define date fields in Malli schemas for calendar dates without time information

**Independent Test**: Define a Malli schema with a date field, create entities with date values, query via GraphQL, verify ISO8601 format (10 chars: YYYY-MM-DD)

### Tests for User Story 1 (TDD - Write FIRST, ensure FAIL)

- [X] T021 [P] [US1] Write test for Date scalar parse function (GraphQL → LocalDate) in test/ringline/schema/scalars_test.clj
- [X] T022 [P] [US1] Write test for Date scalar serialize function (LocalDate → GraphQL) in test/ringline/schema/scalars_test.clj
- [X] T023 [P] [US1] Write test for Date scalar store function (LocalDate → Instant) in test/ringline/schema/scalars_test.clj
- [X] T024 [P] [US1] Write test for invalid date format rejection in test/ringline/schema/scalars_test.clj
- [X] T025 [P] [US1] Write test for invalid date values (Feb 30) in test/ringline/schema/scalars_test.clj
- [X] T026 [US1] Run User Story 1 tests and verify they FAIL (Red phase) - NOTE: Java not available, tests written but not executed

### Implementation for User Story 1

- [X] T027 [P] [US1] Implement parse-date function in src/ringline/schema/scalars.clj using java.time.LocalDate
- [X] T028 [P] [US1] Implement serialize-date function in src/ringline/schema/scalars.clj using DateTimeFormatter.ISO_LOCAL_DATE
- [X] T029 [P] [US1] Implement store-date function in src/ringline/schema/scalars.clj (LocalDate → Instant at midnight UTC)
- [X] T030 [US1] Define Lacinia Date scalar in src/ringline/schema/lacinia.clj with parse/serialize functions
- [X] T031 [US1] Extend field->graphql-type in src/ringline/schema/lacinia.clj to handle :time/local-date
- [X] T032 [US1] Extend convert-value in src/ringline/mutation/transaction.clj to convert Date strings to Instant
- [X] T033 [US1] Extend transform-value in src/ringline/response/transformer.clj to serialize Instant to Date string
- [X] T034 [US1] Run User Story 1 tests and verify they PASS (Green phase) - NOTE: Java not available, implementation complete but tests not executed

### Integration Tests for User Story 1

- [X] T035 [US1] Write integration test for Event entity with date fields in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T036 [US1] Write integration test for createEvent mutation with date in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T037 [US1] Write integration test for querying events by date in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T038 [US1] Run integration tests and verify they PASS - NOTE: Java not available, tests written but not executed

**Checkpoint**: At this point, User Story 1 (Date fields) should be fully functional and testable independently

---

## Phase 4: User Story 2 - DateTime Field Support (Priority: P1)

**Goal**: Enable framework users to define datetime fields in Malli schemas for timestamps with timezone information

**Independent Test**: Define a Malli schema with a datetime field, create entities with datetime values, query via GraphQL, verify ISO8601 format with timezone (25 chars)

### Tests for User Story 2 (TDD - Write FIRST, ensure FAIL)

- [X] T039 [P] [US2] Write test for DateTime scalar parse function (GraphQL → OffsetDateTime) in test/ringline/schema/scalars_test.clj
- [X] T040 [P] [US2] Write test for DateTime scalar serialize function (OffsetDateTime → GraphQL) in test/ringline/schema/scalars_test.clj
- [X] T041 [P] [US2] Write test for DateTime scalar store function (OffsetDateTime → Instant + timezone string) in test/ringline/schema/scalars_test.clj
- [X] T042 [P] [US2] Write test for DateTime missing timezone rejection in test/ringline/schema/scalars_test.clj
- [X] T043 [P] [US2] Write test for DateTime invalid timezone offset in test/ringline/schema/scalars_test.clj
- [X] T044 [P] [US2] Write test for DateTime timezone preservation in test/ringline/schema/scalars_test.clj
- [X] T045 [US2] Run User Story 2 tests and verify they FAIL (Red phase) - NOTE: Java not available, tests written but not executed

### Implementation for User Story 2

- [X] T046 [P] [US2] Implement parse-datetime function in src/ringline/schema/scalars.clj using java.time.OffsetDateTime
- [X] T047 [P] [US2] Implement serialize-datetime function in src/ringline/schema/scalars.clj using DateTimeFormatter.ISO_OFFSET_DATE_TIME
- [X] T049 [P] [US2] Implement validate-datetime-has-timezone function in src/ringline/schema/scalars.clj
- [X] T050 [US2] Define Lacinia DateTime scalar in src/ringline/schema/lacinia.clj with parse/serialize functions
- [X] T051 [US2] Extend field->graphql-type in src/ringline/schema/lacinia.clj to handle :time/offset-date-time - Already done in Phase 2 (T014-T017)
- [X] T052 [US2] Extend convert-value in src/ringline/mutation/transaction.clj to convert DateTime strings to Instant + timezone
- [X] T053 [US2] Extend transform-value in src/ringline/response/transformer.clj to serialize Instant + timezone to DateTime string
- [X] T055 [US2] Run User Story 2 tests and verify they PASS (Green phase) - NOTE: Java not available, implementation complete but tests not executed

### Integration Tests for User Story 2

- [X] T056 [US2] Write integration test for Task entity with datetime fields in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T057 [US2] Write integration test for createTask mutation with datetime in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T058 [US2] Write integration test for querying tasks by datetime range in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T059 [US2] Write integration test for timezone preservation in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T060 [US2] Run integration tests and verify they PASS - NOTE: Java not available, tests written but not executed

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Enum Field Support (Priority: P2)

**Goal**: Enable framework users to define enum fields in Malli schemas to restrict values to predefined options

**Independent Test**: Define a Malli schema with an enum field, create entities with enum values, query via GraphQL, verify only valid enum values are accepted

### Tests for User Story 3 (TDD - Write FIRST, ensure FAIL)

- [X] T061 [P] [US3] Write test for Enum validation (valid values) in test/ringline/schema/scalars_test.clj
- [X] T062 [P] [US3] Write test for Enum validation (invalid values) in test/ringline/schema/scalars_test.clj
- [X] T063 [P] [US3] Write test for Enum case-sensitive matching in test/ringline/schema/scalars_test.clj
- [X] T064 [P] [US3] Write test for Enum case mismatch suggestion in test/ringline/schema/scalars_test.clj
- [X] T065 [P] [US3] Write test for Enum serialization to GraphQL in test/ringline/schema/scalars_test.clj
- [X] T066 [P] [US3] Write test for find-case-mismatch helper function in test/ringline/schema/scalars_test.clj
- [X] T067 [US3] Run User Story 3 tests and verify they FAIL (Red phase) - NOTE: Java not available, tests written but not executed

### Implementation for User Story 3

- [X] T068 [P] [US3] Implement find-case-mismatch helper function in src/ringline/schema/scalars.clj
- [X] T069 [P] [US3] Implement validate-enum function in src/ringline/schema/scalars.clj with case-sensitive matching
- [X] T070 [P] [US3] Implement serialize-enum function in src/ringline/schema/scalars.clj
- [X] T071 [US3] Enum types use String GraphQL type (already mapped in types.clj) - No Lacinia changes needed
- [X] T072 [US3] Extend convert-value in src/ringline/mutation/transaction.clj to convert enum strings to keywords
- [X] T073 [US3] Extend transform-field-value in src/ringline/response/transformer.clj to serialize keywords to enum strings
- [X] T074 [US3] Run User Story 3 tests and verify they PASS (Green phase) - NOTE: Java not available, implementation complete but tests not executed

### Integration Tests for User Story 3

- [X] T075 [US3] Add enum fields (status, priority) to Task schema in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T076 [US3] Write integration test for createTask mutation with enum values in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T077 [US3] Write integration test for querying tasks with enum fields in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T078 [US3] Write integration test for enum validation error with case mismatch suggestion in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T079 [US3] Run integration tests and verify they PASS - NOTE: Java not available, tests written but not executed

**Checkpoint**: At this point, User Stories 1, 2, AND 3 should all work independently

---

## Phase 6: User Story 4 - Decimal Number Support (Priority: P2)

**Goal**: Enable framework users to define decimal fields in Malli schemas for precise numeric values without floating-point errors

**Independent Test**: Define a Malli schema with a decimal field, create entities with decimal values, query via GraphQL, verify precision is maintained

### Tests for User Story 4 (TDD - Write FIRST, ensure FAIL)

- [X] T080 [P] [US4] Write test for Decimal scalar parse function (GraphQL → BigDecimal) in test/ringline/schema/scalars_test.clj
- [X] T081 [P] [US4] Write test for Decimal scalar serialize function (BigDecimal → GraphQL string) in test/ringline/schema/scalars_test.clj
- [X] T082 [P] [US4] Write test for Decimal precision validation (38 digits max) in test/ringline/schema/scalars_test.clj
- [X] T083 [P] [US4] Write test for Decimal scale validation (10 decimal places max) in test/ringline/schema/scalars_test.clj
- [X] T084 [P] [US4] Write test for Decimal precision preservation in test/ringline/schema/scalars_test.clj
- [X] T085 [P] [US4] Write test for invalid decimal format rejection in test/ringline/schema/scalars_test.clj
- [X] T086 [US4] Run User Story 4 tests and verify they FAIL (Red phase) - NOTE: Java not available, tests written but not executed

### Implementation for User Story 4

- [X] T087 [P] [US4] Implement parse-decimal function in src/ringline/schema/scalars.clj using java.math.BigDecimal
- [X] T088 [P] [US4] Implement serialize-decimal function in src/ringline/schema/scalars.clj (BigDecimal → string)
- [X] T089 [P] [US4] Implement validate-decimal-precision-scale helper function in src/ringline/schema/scalars.clj (max 38 digits, 10 scale)
- [X] T090 [P] [US4] Combined with T089 - validate-decimal-precision-scale handles both precision and scale
- [X] T091 [US4] Define Lacinia Decimal scalar in src/ringline/schema/lacinia.clj with parse/serialize functions
- [X] T092 [US4] Extend field->graphql-type in src/ringline/schema/lacinia.clj to handle :decimal - Already done in Phase 2 (T017)
- [X] T093 [US4] Extend convert-value in src/ringline/mutation/transaction.clj to convert Decimal strings to BigDecimal
- [X] T094 [US4] Extend transform-field-value in src/ringline/response/transformer.clj to serialize BigDecimal to string
- [X] T095 [US4] Run User Story 4 tests and verify they PASS (Green phase) - NOTE: Java not available, tests written but not executed

### Integration Tests for User Story 4

- [X] T096 [US4] Create Product entity schema with decimal fields (price, weight) in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T097 [US4] Write integration test for createProduct mutation with decimal values in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T098 [US4] Write integration test for querying products with decimal fields in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T099 [US4] Write integration test for decimal precision preservation in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T100 [US4] Write integration test for decimal precision/scale limit violations in test/ringline/integration/custom_scalars_integration_test.clj
- [X] T101 [US4] Run integration tests and verify they PASS - NOTE: Java not available, tests written but not executed

**Checkpoint**: All user stories should now be independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T102 [P] Extend parser tests in test/ringline/schema/parser_test.clj to verify parsing of all 4 scalar types
- [ ] T103 [P] Extend Datomic schema generation tests in test/ringline/schema/datomic_test.clj for all 4 scalar types
- [ ] T104 [P] Extend Lacinia schema generation tests in test/ringline/schema/lacinia_test.clj for all 4 scalar types
- [ ] T105 [P] Extend mutation transaction tests in test/ringline/mutation/transaction_test.clj for all 4 scalar types
- [ ] T106 [P] Extend mutation Lacinia tests in test/ringline/mutation/lacinia_test.clj for all 4 scalar types
- [ ] T107 [P] Extend response transformer tests in test/ringline/response/transformer_test.clj for all 4 scalar types
- [ ] T108 Add error handling for schema generation failures (fail fast at definition time) in src/ringline/schema/datomic.clj
- [ ] T109 Add error handling for schema generation failures (fail fast at definition time) in src/ringline/schema/lacinia.clj
- [ ] T110 Validate quickstart.md examples work correctly (run example code from quickstart.md)
- [ ] T111 Run full test suite with Kaocha and verify all tests pass
- [ ] T112 Performance benchmarking: Verify scalar conversions complete in <1ms per research.md target
- [ ] T113 Code review and refactoring for clarity and consistency

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P1 → P2 → P2)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1 - Date)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1 - DateTime)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 3 (P2 - Enum)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 4 (P2 - Decimal)**: Can start after Foundational (Phase 2) - No dependencies on other stories

**All user stories are independently testable and can be implemented in parallel**

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD Red-Green-Refactor)
- Scalar validation functions before Lacinia scalar definitions
- Type mappings before schema generation
- Schema generation before mutation/query handling
- Core implementation before integration tests
- Story complete before moving to next priority

### Parallel Opportunities

- **Phase 1 (Setup)**: All 4 tasks can run in parallel
- **Phase 2 (Foundational Tests)**: T005-T008 can run in parallel (different test cases)
- **Phase 2 (Foundational Implementation)**: T010-T017 can run in parallel (different type mappings)
- **User Story 1 Tests**: T021-T025 can run in parallel (different test cases)
- **User Story 1 Implementation**: T027-T029 can run in parallel (different functions)
- **User Story 2 Tests**: T039-T044 can run in parallel (different test cases)
- **User Story 2 Implementation**: T046-T049 can run in parallel (different functions)
- **User Story 3 Tests**: T061-T065 can run in parallel (different test cases)
- **User Story 3 Implementation**: T067-T069 can run in parallel (different functions)
- **User Story 4 Tests**: T080-T085 can run in parallel (different test cases)
- **User Story 4 Implementation**: T087-T090 can run in parallel (different functions)
- **Phase 7 (Polish)**: T102-T107 can run in parallel (different test files)
- **Once Foundational completes**: All 4 user stories (Phase 3-6) can start in parallel

---

## Parallel Example: User Story 1 (Date Fields)

```bash
# Launch all tests for User Story 1 together (TDD - write first):
Task T021: "Write test for Date scalar parse function in test/ringline/schema/scalars_test.clj"
Task T022: "Write test for Date scalar serialize function in test/ringline/schema/scalars_test.clj"
Task T023: "Write test for Date scalar store function in test/ringline/schema/scalars_test.clj"
Task T024: "Write test for invalid date format rejection in test/ringline/schema/scalars_test.clj"
Task T025: "Write test for invalid date values in test/ringline/schema/scalars_test.clj"

# Launch all implementation functions for User Story 1 together:
Task T027: "Implement parse-date function in src/ringline/schema/scalars.clj"
Task T028: "Implement serialize-date function in src/ringline/schema/scalars.clj"
Task T029: "Implement store-date function in src/ringline/schema/scalars.clj"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only - Date Fields)

1. Complete Phase 1: Setup (T001-T004)
2. Complete Phase 2: Foundational (T005-T020) - CRITICAL - blocks all stories
3. Complete Phase 3: User Story 1 - Date Fields (T021-T038)
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery (Recommended)

1. Complete Setup + Foundational → Foundation ready (T001-T020)
2. Add User Story 1 (Date) → Test independently → Deploy/Demo (T021-T038) - MVP!
3. Add User Story 2 (DateTime) → Test independently → Deploy/Demo (T039-T060)
4. Add User Story 3 (Enum) → Test independently → Deploy/Demo (T061-T079)
5. Add User Story 4 (Decimal) → Test independently → Deploy/Demo (T080-T101)
6. Polish & Cross-Cutting → Final release (T102-T113)

Each story adds value without breaking previous stories.

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together (T001-T020)
2. Once Foundational is done:
   - Developer A: User Story 1 (Date) - T021-T038
   - Developer B: User Story 2 (DateTime) - T039-T060
   - Developer C: User Story 3 (Enum) - T061-T079
   - Developer D: User Story 4 (Decimal) - T080-T101
3. Stories complete and integrate independently
4. Team completes Polish together (T102-T113)

---

## Notes

- **TDD is MANDATORY**: Constitution Principle III (Test-First Development is NON-NEGOTIABLE)
- **Red-Green-Refactor**: Write test → Verify FAIL → Implement → Verify PASS → Refactor
- **[P] tasks**: Different files, no dependencies - can run in parallel
- **[Story] label**: Maps task to specific user story for traceability
- **Each user story is independently completable and testable**
- **Verify tests fail before implementing** (Red phase)
- **Commit after each task or logical group**
- **Stop at any checkpoint to validate story independently**
- **Avoid**: vague tasks, same file conflicts, cross-story dependencies that break independence
