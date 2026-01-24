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

- [ ] T001 Verify Clojure 1.12.0, Malli 0.20.0, Lacinia 1.3.0-beta-1, Datomic Free 0.9.5697 dependencies in deps.edn
- [ ] T002 Create new namespace file src/ringline/schema/scalars.clj for custom scalar logic
- [ ] T003 Create new test file test/ringline/schema/scalars_test.clj for scalar validation tests
- [ ] T004 Create new integration test file test/ringline/integration/custom_scalars_integration_test.clj

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core type system extensions that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational (TDD - Write FIRST, ensure FAIL)

- [ ] T005 [P] Write test for Date type mapping in test/ringline/schema/types_test.clj
- [ ] T006 [P] Write test for DateTime type mapping in test/ringline/schema/types_test.clj
- [ ] T007 [P] Write test for Enum type mapping in test/ringline/schema/types_test.clj
- [ ] T008 [P] Write test for Decimal type mapping in test/ringline/schema/types_test.clj
- [ ] T009 Run tests and verify they FAIL (Red phase)

### Implementation for Foundational

- [ ] T010 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :time/local-date → :db.type/instant
- [ ] T011 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :time/offset-date-time → :db.type/instant
- [ ] T012 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :enum → :db.type/keyword
- [ ] T013 [P] Extend malli->datomic map in src/ringline/schema/types.clj to add :decimal → :db.type/bigdec
- [ ] T014 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :time/local-date → 'Date
- [ ] T015 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :time/offset-date-time → 'DateTime
- [ ] T016 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :enum → (enum type generation)
- [ ] T017 [P] Extend malli->graphql map in src/ringline/schema/types.clj to add :decimal → 'Decimal
- [ ] T018 Run tests and verify they PASS (Green phase)

### Malli Registry Setup

- [ ] T019 Register :time/local-date and :time/offset-date-time from malli.experimental.time in src/ringline/schema/scalars.clj
- [ ] T020 Implement custom :decimal schema using IntoSchema protocol in src/ringline/schema/scalars.clj with precision/scale validation

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Date Field Support (Priority: P1) 🎯 MVP

**Goal**: Enable framework users to define date fields in Malli schemas for calendar dates without time information

**Independent Test**: Define a Malli schema with a date field, create entities with date values, query via GraphQL, verify ISO8601 format (10 chars: YYYY-MM-DD)

### Tests for User Story 1 (TDD - Write FIRST, ensure FAIL)

- [ ] T021 [P] [US1] Write test for Date scalar parse function (GraphQL → LocalDate) in test/ringline/schema/scalars_test.clj
- [ ] T022 [P] [US1] Write test for Date scalar serialize function (LocalDate → GraphQL) in test/ringline/schema/scalars_test.clj
- [ ] T023 [P] [US1] Write test for Date scalar store function (LocalDate → Instant) in test/ringline/schema/scalars_test.clj
- [ ] T024 [P] [US1] Write test for invalid date format rejection in test/ringline/schema/scalars_test.clj
- [ ] T025 [P] [US1] Write test for invalid date values (Feb 30) in test/ringline/schema/scalars_test.clj
- [ ] T026 [US1] Run User Story 1 tests and verify they FAIL (Red phase)

### Implementation for User Story 1

- [ ] T027 [P] [US1] Implement parse-date function in src/ringline/schema/scalars.clj using java.time.LocalDate
- [ ] T028 [P] [US1] Implement serialize-date function in src/ringline/schema/scalars.clj using DateTimeFormatter.ISO_LOCAL_DATE
- [ ] T029 [P] [US1] Implement store-date function in src/ringline/schema/scalars.clj (LocalDate → Instant at midnight UTC)
- [ ] T030 [US1] Define Lacinia Date scalar in src/ringline/schema/lacinia.clj with parse/serialize functions
- [ ] T031 [US1] Extend field->graphql-type in src/ringline/schema/lacinia.clj to handle :time/local-date
- [ ] T032 [US1] Extend convert-value in src/ringline/mutation/transaction.clj to convert Date strings to Instant
- [ ] T033 [US1] Extend transform-value in src/ringline/response/transformer.clj to serialize Instant to Date string
- [ ] T034 [US1] Run User Story 1 tests and verify they PASS (Green phase)

### Integration Tests for User Story 1

- [ ] T035 [US1] Write integration test for Event entity with date fields in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T036 [US1] Write integration test for createEvent mutation with date in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T037 [US1] Write integration test for querying events by date in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T038 [US1] Run integration tests and verify they PASS

**Checkpoint**: At this point, User Story 1 (Date fields) should be fully functional and testable independently

---

## Phase 4: User Story 2 - DateTime Field Support (Priority: P1)

**Goal**: Enable framework users to define datetime fields in Malli schemas for timestamps with timezone information

**Independent Test**: Define a Malli schema with a datetime field, create entities with datetime values, query via GraphQL, verify ISO8601 format with timezone (25 chars)

### Tests for User Story 2 (TDD - Write FIRST, ensure FAIL)

- [ ] T039 [P] [US2] Write test for DateTime scalar parse function (GraphQL → OffsetDateTime) in test/ringline/schema/scalars_test.clj
- [ ] T040 [P] [US2] Write test for DateTime scalar serialize function (OffsetDateTime → GraphQL) in test/ringline/schema/scalars_test.clj
- [ ] T041 [P] [US2] Write test for DateTime scalar store function (OffsetDateTime → Instant + timezone string) in test/ringline/schema/scalars_test.clj
- [ ] T042 [P] [US2] Write test for DateTime missing timezone rejection in test/ringline/schema/scalars_test.clj
- [ ] T043 [P] [US2] Write test for DateTime invalid timezone offset in test/ringline/schema/scalars_test.clj
- [ ] T044 [P] [US2] Write test for DateTime timezone preservation in test/ringline/schema/scalars_test.clj
- [ ] T045 [US2] Run User Story 2 tests and verify they FAIL (Red phase)

### Implementation for User Story 2

- [ ] T046 [P] [US2] Implement parse-datetime function in src/ringline/schema/scalars.clj using java.time.OffsetDateTime
- [ ] T047 [P] [US2] Implement serialize-datetime function in src/ringline/schema/scalars.clj using DateTimeFormatter.ISO_OFFSET_DATE_TIME
- [ ] T049 [P] [US2] Implement validate-datetime-has-timezone function in src/ringline/schema/scalars.clj
- [ ] T050 [US2] Define Lacinia DateTime scalar in src/ringline/schema/lacinia.clj with parse/serialize functions
- [ ] T051 [US2] Extend field->graphql-type in src/ringline/schema/lacinia.clj to handle :time/offset-date-time
- [ ] T052 [US2] Extend convert-value in src/ringline/mutation/transaction.clj to convert DateTime strings to Instant + timezone
- [ ] T053 [US2] Extend transform-value in src/ringline/response/transformer.clj to serialize Instant + timezone to DateTime string
- [ ] T055 [US2] Run User Story 2 tests and verify they PASS (Green phase)

### Integration Tests for User Story 2

- [ ] T056 [US2] Write integration test for Task entity with datetime fields in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T057 [US2] Write integration test for createTask mutation with datetime in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T058 [US2] Write integration test for querying tasks by datetime range in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T059 [US2] Write integration test for timezone preservation in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T060 [US2] Run integration tests and verify they PASS

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Enum Field Support (Priority: P2)

**Goal**: Enable framework users to define enum fields in Malli schemas to restrict values to predefined options

**Independent Test**: Define a Malli schema with an enum field, create entities with enum values, query via GraphQL, verify only valid enum values are accepted

### Tests for User Story 3 (TDD - Write FIRST, ensure FAIL)

- [ ] T061 [P] [US3] Write test for Enum validation (valid values) in test/ringline/schema/scalars_test.clj
- [ ] T062 [P] [US3] Write test for Enum validation (invalid values) in test/ringline/schema/scalars_test.clj
- [ ] T063 [P] [US3] Write test for Enum case-sensitive matching in test/ringline/schema/scalars_test.clj
- [ ] T064 [P] [US3] Write test for Enum case mismatch suggestion in test/ringline/schema/scalars_test.clj
- [ ] T065 [P] [US3] Write test for Enum serialization to GraphQL in test/ringline/schema/scalars_test.clj
- [ ] T066 [US3] Run User Story 3 tests and verify they FAIL (Red phase)

### Implementation for User Story 3

- [ ] T067 [P] [US3] Implement validate-enum function in src/ringline/schema/scalars.clj with case-sensitive matching
- [ ] T068 [P] [US3] Implement suggest-enum-case-mismatch function in src/ringline/schema/scalars.clj
- [ ] T069 [P] [US3] Implement enum-error-message function in src/ringline/schema/scalars.clj with suggestions
- [ ] T070 [US3] Extend field->graphql-type in src/ringline/schema/lacinia.clj to generate GraphQL enum types from Malli :enum
- [ ] T071 [US3] Extend malli-type->graphql-type in src/ringline/mutation/lacinia.clj to handle :enum in mutation arguments
- [ ] T072 [US3] Extend convert-value in src/ringline/mutation/transaction.clj to convert enum strings to keywords
- [ ] T073 [US3] Extend transform-value in src/ringline/response/transformer.clj to serialize keywords to enum strings
- [ ] T074 [US3] Run User Story 3 tests and verify they PASS (Green phase)

### Integration Tests for User Story 3

- [ ] T075 [US3] Write integration test for Task entity with enum fields (status, priority) in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T076 [US3] Write integration test for createTask mutation with enum values in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T077 [US3] Write integration test for querying tasks by enum value in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T078 [US3] Write integration test for enum validation error with case mismatch suggestion in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T079 [US3] Run integration tests and verify they PASS

**Checkpoint**: At this point, User Stories 1, 2, AND 3 should all work independently

---

## Phase 6: User Story 4 - Decimal Number Support (Priority: P2)

**Goal**: Enable framework users to define decimal fields in Malli schemas for precise numeric values without floating-point errors

**Independent Test**: Define a Malli schema with a decimal field, create entities with decimal values, query via GraphQL, verify precision is maintained

### Tests for User Story 4 (TDD - Write FIRST, ensure FAIL)

- [ ] T080 [P] [US4] Write test for Decimal scalar parse function (GraphQL → BigDecimal) in test/ringline/schema/scalars_test.clj
- [ ] T081 [P] [US4] Write test for Decimal scalar serialize function (BigDecimal → GraphQL string) in test/ringline/schema/scalars_test.clj
- [ ] T082 [P] [US4] Write test for Decimal precision validation (38 digits max) in test/ringline/schema/scalars_test.clj
- [ ] T083 [P] [US4] Write test for Decimal scale validation (10 decimal places max) in test/ringline/schema/scalars_test.clj
- [ ] T084 [P] [US4] Write test for Decimal precision preservation in test/ringline/schema/scalars_test.clj
- [ ] T085 [P] [US4] Write test for invalid decimal format rejection in test/ringline/schema/scalars_test.clj
- [ ] T086 [US4] Run User Story 4 tests and verify they FAIL (Red phase)

### Implementation for User Story 4

- [ ] T087 [P] [US4] Implement parse-decimal function in src/ringline/schema/scalars.clj using java.math.BigDecimal
- [ ] T088 [P] [US4] Implement serialize-decimal function in src/ringline/schema/scalars.clj (BigDecimal → string)
- [ ] T089 [P] [US4] Implement validate-decimal-precision function in src/ringline/schema/scalars.clj (max 38 digits)
- [ ] T090 [P] [US4] Implement validate-decimal-scale function in src/ringline/schema/scalars.clj (max 10 decimal places)
- [ ] T091 [US4] Define Lacinia Decimal scalar in src/ringline/schema/lacinia.clj with parse/serialize functions
- [ ] T092 [US4] Extend field->graphql-type in src/ringline/schema/lacinia.clj to handle :decimal
- [ ] T093 [US4] Extend convert-value in src/ringline/mutation/transaction.clj to convert Decimal strings to BigDecimal
- [ ] T094 [US4] Extend transform-value in src/ringline/response/transformer.clj to serialize BigDecimal to string
- [ ] T095 [US4] Run User Story 4 tests and verify they PASS (Green phase)

### Integration Tests for User Story 4

- [ ] T096 [US4] Write integration test for Product entity with decimal fields (price, weight) in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T097 [US4] Write integration test for createProduct mutation with decimal values in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T098 [US4] Write integration test for querying products by price range in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T099 [US4] Write integration test for decimal precision preservation in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T100 [US4] Write integration test for decimal precision/scale limit violations in test/ringline/integration/custom_scalars_integration_test.clj
- [ ] T101 [US4] Run integration tests and verify they PASS

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
