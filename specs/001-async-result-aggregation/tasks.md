# Tasks: Java Asynchronous HTTP Client & Result Aggregator

**Input**: Design documents in `specs/001-async-result-aggregation/`
**Required stack**: Java 21, Maven, `CompletableFuture`, `HttpClient.sendAsync`, Jetty 12,
Jackson 2.x, JUnit 5
**Status rule**: Every task is incomplete for Java. Prior Python completion through T022 is
historical evidence only in `verification.md` and cannot satisfy any checkbox below.

## Format: `[ID] [P?] [Story] Description`

- `[P]` means the task can run concurrently in a different file after its stated prerequisites.
- Story labels map directly to `spec.md`.
- Tests are included because the constitution and success criteria require automated evidence.

## Phase 1: Java Project Setup

**Purpose**: Establish a reproducible Java-only build without changing or deleting Python files.

- [x] T001 Create `pom.xml` with Java release 21, pinned Jetty 12 and Jackson 2.x runtime dependencies, pinned JUnit 5 test dependency, Surefire, compiler, and executable-JAR plugins; exclude historical Python paths from the Maven build.
- [x] T002 Create Maven Wrapper files `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, and `mvnw.cmd`, then record wrapper and JDK prerequisites in `README.md`.
- [x] T003 [P] Create Java package scaffolding and an import-safe entry point in `src/main/java/edu/university/aggregator/Main.java` without implementing feature behavior.

**Checkpoint**: `mvnw.cmd -q -DskipTests package` compiles an empty Java 21 application; Python
source and tests remain byte-for-byte untouched.

## Phase 2: Foundational Domain, Validation, and JSON

**Purpose**: Define immutable contracts and reject invalid work before network I/O.

### Tests first

- [x] T004 [P] Write failing Java record/enum invariant tests for every entity in `src/test/java/edu/university/aggregator/unit/ModelTest.java`.
- [x] T005 [P] Write failing collect-all validation tests for FR-001, FR-002, FR-021, FR-022, and SC-007 in `src/test/java/edu/university/aggregator/unit/RunConfigurationValidatorTest.java`.
- [x] T006 [P] Write failing Jackson serialization tests for FR-012–FR-016, SC-001, and SC-009 in `src/test/java/edu/university/aggregator/unit/JsonCodecTest.java`.

### Shared implementation

- [x] T007 Implement `WorkerBehavior`, `WorkerStatus`, and `FailureCode` closed enums in `src/main/java/edu/university/aggregator/model/` with the exact contract values from `data-model.md`.
- [x] T008 Implement immutable configuration and result records in `src/main/java/edu/university/aggregator/model/`, enforcing worker ID “trimmed, non-empty; maximum 64 characters”, credential-free absolute HTTP(S) URL, GET-only method, `timeout_ms > 0`, `delay_ms >= 0`, finite non-negative durations, success/failure field invariants, and configured order (depends on T004, T007; FR-012–FR-016).
- [x] T009 Implement strict Jackson parsing/serialization and the exact ordered validation-error envelope in `src/main/java/edu/university/aggregator/json/JsonCodec.java`, rejecting unknown fields and non-finite durations (depends on T006, T008).
- [x] T010 Implement stable collect-all preflight validation with no I/O in `src/main/java/edu/university/aggregator/config/RunConfigurationValidator.java` (depends on T005, T008; FR-001, FR-002, FR-021, FR-022, SC-007).
- [x] T011 [P] Create deterministic sample builders, the tolerance `max(150 ms, 25% of longest delay/timeout)`, and lifecycle assertions in `src/test/java/edu/university/aggregator/TestSupport.java`.

**Checkpoint**: Foundation JUnit tests pass; invalid input reports every detectable field error and
cannot construct a network-ready run.

## Phase 3: User Story 1 — Aggregate Successful Worker Results (Priority: P1) MVP

**Goal**: Prove concurrent successful aggregation, deterministic order, and timing.

**Independent Test**: Three differently delayed Jetty workers produce one ordered report whose
duration is near the longest delay; 20 simultaneous runs preserve exact identity and association.

### Tests first

- [x] T012 [P] [US1] Write failing Jetty `GET /work` and `GET /health` tests for success, null data, scheduled delay, OS-assigned ports, and cleanup in `src/test/java/edu/university/aggregator/integration/WorkerContractTest.java` (FR-003, SC-008).
- [x] T013 [P] [US1] Write failing successful `sendAsync` client mapping tests in `src/test/java/edu/university/aggregator/unit/WorkerHttpClientTest.java` (FR-005, FR-013, FR-014).
- [x] T014 [P] [US1] Write failing real-loopback three/five-worker concurrency, configured-order, association, and duration tests in `src/test/java/edu/university/aggregator/integration/ConcurrentAggregationTest.java` (FR-004, FR-005, FR-012–FR-018, SC-001, SC-003, SC-009).
- [x] T015 [P] [US1] Write the failing 0.15/0.30/0.45-second concurrent-versus-sequential test in `src/test/java/edu/university/aggregator/performance/ConcurrentPerformanceTest.java` with all SC-004 thresholds.
- [x] T016 [P] [US1] Write a failing 20-run simultaneous-completion test in `src/test/java/edu/university/aggregator/integration/SimultaneousCompletionTest.java` (FR-016, FR-017, FR-020, SC-002).
- [x] T017 [P] [US1] Write failing `list-scenarios`, successful `demo`, standalone worker readiness, stream separation, file output, and exit-0 tests in `src/test/java/edu/university/aggregator/integration/CliTest.java` (SC-008, SC-009).

### Implementation

- [x] T018 [P] [US1] Implement successful/delayed embedded Jetty workers, scheduled non-blocking responses, loopback OS-port discovery, and reverse-order fleet cleanup in `src/main/java/edu/university/aggregator/worker/` (depends on T012; FR-003).
- [x] T019 [P] [US1] Implement one worker operation with shared Java 21 `HttpClient`, `sendAsync`, finite connection/whole-operation deadlines, response checks, and `System.nanoTime()` in `src/main/java/edu/university/aggregator/client/WorkerHttpClient.java` (depends on T013; FR-005, FR-006, FR-013, FR-014).
- [x] T020 [US1] Implement bounded future orchestration, immediate future creation, `CompletableFuture.allOf`, configured-order collection, derived successful data, and total timing in `src/main/java/edu/university/aggregator/service/AggregationService.java` (depends on T014, T018, T019; FR-004–FR-007, FR-012–FR-019).
- [x] T021 [US1] Define deterministic `normal` and `simultaneous` scenarios in `src/main/java/edu/university/aggregator/scenario/ScenarioCatalog.java` (depends on T018, T020; FR-003, FR-020).
- [x] T022 [US1] Implement `list-scenarios`, successful `demo`, and standalone `worker` in `src/main/java/edu/university/aggregator/cli/AggregatorCli.java` and delegate from `Main.java` with JSON stdout, diagnostics stderr, output files, and shutdown cleanup (depends on T017–T021).
- [x] T023 [US1] Run the Java US1 JUnit groups and append JDK/Maven versions, 20-run results, and measured timings under `Java 21 Verification` in `specs/001-async-result-aggregation/verification.md` (depends on T012–T022; SC-001, SC-002, SC-004, SC-008, SC-009).

**Checkpoint**: US1 is independently demonstrable from the executable JAR with Java-only evidence.

## Phase 4: User Story 2 — Preserve Partial Results During Worker Failures (Priority: P2)

**Goal**: Isolate supported failures, timeouts, and cancellation while retaining successes.

**Independent Test**: Mix success, HTTP 500, unavailable, malformed, and timeout workers; then cancel
an active run and prove prompt cleanup and ordered partial cancellation outcomes.

### Tests first

- [x] T024 [P] [US2] Extend `src/test/java/edu/university/aggregator/unit/WorkerHttpClientTest.java` with HTTP 500, connect/network/protocol, invalid JSON/shape/identity, timeout, and unsuccessful-null-data cases (FR-006–FR-010, FR-014, SC-003, SC-005).
- [x] T025 [P] [US2] Extend `src/test/java/edu/university/aggregator/integration/WorkerContractTest.java` with HTTP 500, malformed response, scheduled-work cancellation, and Jetty cleanup cases (FR-003, FR-008, FR-009).
- [x] T026 [P] [US2] Write mixed-failure isolation tests in `src/test/java/edu/university/aggregator/integration/FailureIsolationTest.java` (FR-008–FR-014, SC-001, SC-003).
- [x] T027 [P] [US2] Write independent timeout-boundary tests in `src/test/java/edu/university/aggregator/integration/TimeoutTest.java` (FR-006, FR-007, FR-019, SC-005).
- [x] T028 [P] [US2] Write cancellation and owned-resource cleanup tests in `src/test/java/edu/university/aggregator/integration/CancellationCleanupTest.java` (FR-024).
- [x] T029 [P] [US2] Write diagnostic redaction tests in `src/test/java/edu/university/aggregator/unit/DiagnosticRedactionTest.java` (FR-023).

### Implementation

- [x] T030 [P] [US2] Add fixed HTTP-500 and malformed behaviors plus cancellation-safe scheduled responses in `src/main/java/edu/university/aggregator/worker/` (depends on T025; FR-003, FR-009).
- [x] T031 [P] [US2] Implement explicit Java exception unwrapping/mapping and sanitized stable codes in `src/main/java/edu/university/aggregator/client/WorkerHttpClient.java` (depends on T024; FR-006–FR-010, FR-014).
- [x] T032 [US2] Implement parent cancellation in `src/main/java/edu/university/aggregator/service/AggregationService.java`: cancel outstanding futures, retain settled outcomes, mark remaining outcomes cancelled, close owned resources, and expose `complete=false` partial report (depends on T028, T031; FR-024).
- [x] T033 [US2] Define deterministic `http-500`, `unavailable`, `timeout`, and `partial-failure` scenarios in `src/main/java/edu/university/aggregator/scenario/ScenarioCatalog.java` (depends on T026, T027, T030–T032; FR-020).
- [x] T034 [US2] Add sanitized terminal diagnostics in `src/main/java/edu/university/aggregator/client/WorkerHttpClient.java` and `src/main/java/edu/university/aggregator/service/AggregationService.java` (depends on T029, T031, T032; FR-023).
- [x] T035 [US2] Extend `demo` for partial-failure scenarios in `src/main/java/edu/university/aggregator/cli/AggregatorCli.java` with exit 0 for valid remote-failure reports (depends on T033, T034).
- [x] T036 [US2] Run all Java US2 tests and append failure mapping, timeout, cancellation, redaction, and cleanup evidence to `specs/001-async-result-aggregation/verification.md` (depends on T024–T035; SC-003, SC-005, SC-008, SC-009).

## Phase 5: User Story 3 — Diagnose Complete Failure and Invalid Configuration (Priority: P3)

**Goal**: Distinguish valid total remote failure from invalid local configuration.

**Independent Test**: All-failure returns a valid empty-success report; multi-error invalid input
returns exit 2, every field error, empty stdout, and zero request attempts.

### Tests first

- [x] T037 [P] [US3] Write all-failure/all-timeout tests in `src/test/java/edu/university/aggregator/integration/CompleteFailureTest.java` (FR-008, FR-012, FR-014, FR-019, SC-006).
- [x] T038 [P] [US3] Write invalid-configuration tests with a request-start spy in `src/test/java/edu/university/aggregator/integration/InvalidConfigurationTest.java` (FR-001, FR-002, FR-021, FR-022, SC-007).
- [x] T039 [P] [US3] Extend `src/test/java/edu/university/aggregator/integration/CliTest.java` with `aggregate`, all-fail, JSON loading, structured stderr, file output, and exit-code cases (SC-006, SC-007, SC-009).

### Implementation

- [x] T040 [US3] Define deterministic `all-fail` composition in `src/main/java/edu/university/aggregator/scenario/ScenarioCatalog.java` (depends on T031–T033, T037; FR-020).
- [x] T041 [US3] Implement `aggregate --config`, strict JSON loading, collect-all rejection, all-fail dispatch, routing, and exit codes in `src/main/java/edu/university/aggregator/cli/AggregatorCli.java` (depends on T038–T040; FR-021, FR-022).
- [x] T042 [US3] Create contract-valid `examples/run-normal.json`, `examples/run-partial-failure.json`, `examples/run-standalone.json`, and multi-error `examples/invalid-run.json` (depends on T041; SC-007, SC-008).
- [ ] T043 [US3] Run Java US3 tests and append total-failure, exit-code, no-I/O-on-rejection, and report evidence to `specs/001-async-result-aggregation/verification.md` (depends on T037–T042; SC-006, SC-007, SC-009).

## Phase 6: Cross-Story Verification and Documentation

- [ ] T044 [P] Add JSON contract-consistency tests in `src/test/java/edu/university/aggregator/integration/ContractConsistencyTest.java` for every invariant in both schemas.
- [ ] T045 [P] Add the repeated eight-scenario acceptance matrix in `src/test/java/edu/university/aggregator/integration/AcceptanceScenarioTest.java` (FR-001–FR-024, SC-001–SC-009).
- [ ] T046 [P] Add repeated lifecycle verification for success, failure, timeout, and cancellation in `src/test/java/edu/university/aggregator/integration/ResourceCleanupTest.java`.
- [ ] T047 Run `mvnw.cmd clean verify` and append commands, environment, dependency versions, timings, test counts, and failures/passes to `specs/001-async-result-aggregation/verification.md` (depends on T044–T046).
- [ ] T048 Audit FR-001–FR-024 and SC-001–SC-009 against Java source and named JUnit tests in `specs/001-async-result-aggregation/verification.md` (depends on T047).
- [ ] T049 Audit `pom.xml` for Java 21 and constitution-approved dependencies, then record dependency-tree and executable-JAR smoke evidence in `specs/001-async-result-aggregation/verification.md` (depends on T001–T048).
- [ ] T050 [P] Rewrite `README.md` for Java 21/Maven setup, architecture, commands, scenarios, output, and contract links while preserving a clearly labeled Python-prototype history note.
- [ ] T051 Execute every command in `specs/001-async-result-aggregation/quickstart.md` and append reproducibility evidence to `specs/001-async-result-aggregation/verification.md` (depends on T047, T050).
- [ ] T052 Perform and record the final constitution v2.0.0 review for Java-only source, `sendAsync`, `CompletableFuture`, bounded resources, deterministic results, failure isolation, cleanup, redaction, approved dependencies, and JUnit evidence in `specs/001-async-result-aggregation/verification.md` (depends on T051).

## Dependencies and Execution Order

```text
Setup T001–T003
  -> Foundation T004–T011
    -> US1 T012–T023 (MVP)
      -> US2 T024–T036
        -> US3 T037–T043
          -> Cross-story T044–T052
```

Tests within a story may be authored in parallel. US2 and US3 test authoring may begin after the
foundation, but implementation joins the named earlier components. Verification tasks depend on all
implementation tasks in their phase. No migrated task may be marked complete from Python evidence.

## Requirement and Success-Criterion Traceability

| Requirement | Java implementation tasks | Java verification tasks |
|---|---|---|
| FR-001–FR-002 | T008, T010, T041 | T005, T038, T045, T048 |
| FR-003 | T018, T021, T030, T033 | T012, T025, T045, T048 |
| FR-004–FR-005 | T019–T020 | T013–T015, T045, T048 |
| FR-006–FR-007 | T019–T020, T031 | T024, T027, T045, T048 |
| FR-008–FR-011 | T008, T030–T032 | T024–T028, T037, T045, T048 |
| FR-012–FR-017 | T008–T009, T020, T031–T032 | T006, T014, T016, T026, T037, T044–T045, T048 |
| FR-018–FR-019 | T020, T031, T033 | T015, T027, T037, T045, T048 |
| FR-020 | T021, T033, T040 | T016, T023, T036, T043, T045, T048 |
| FR-021–FR-022 | T010, T041 | T005, T038–T039, T045, T048 |
| FR-023 | T034 | T029, T045, T048 |
| FR-024 | T032 | T028, T046, T048 |
| SC-001–SC-003 | T008, T020, T031 | T014, T016, T024, T026, T037, T045, T048 |
| SC-004–SC-005 | T019–T020, T031, T033 | T015, T023–T024, T027, T036, T047–T048 |
| SC-006–SC-007 | T010, T040–T042 | T037–T039, T043, T045, T048 |
| SC-008–SC-009 | T009, T018, T021–T022, T033, T040–T041 | T012, T017, T023, T036, T039, T043–T045, T048, T051 |

## Parallel Opportunities

- T004–T006 and T011 target separate foundation test files.
- T012–T017 are parallel test-first work; T018 and T019 can then proceed concurrently.
- T024–T029 are parallel test-first work; T030 and T031 can then proceed concurrently.
- T037–T039 are parallel after required earlier interfaces stabilize.
- T044–T046 and T050 target separate files after story checkpoints.

## Implementation Strategy

Deliver T001–T023 as the Java MVP, validate it independently, then add US2 reliability, US3
diagnosis, and finally the full matrix and reproducibility gate. Preserve the Python implementation
throughout; compare behavior only through published contracts, never by importing Python into Java.
