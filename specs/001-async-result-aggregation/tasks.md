---

description: "Dependency-ordered implementation tasks for the asynchronous HTTP result aggregator"
---

# Tasks: Asynchronous HTTP Client & Result Aggregator

**Input**: Approved design documents from `specs/001-async-result-aggregation/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, and
`quickstart.md`

**Tests**: Tests are mandatory under the constitution and are written before or alongside the
behavior they verify. Each test task must fail for the expected missing behavior before its paired
implementation task begins.

**Organization**: Setup and foundational work precede three independently testable user-story
increments. Cross-story verification and documentation follow the story checkpoints.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it targets different files and has no dependency on an
  incomplete task in the same phase.
- **[Story]**: Maps a task to User Story 1, 2, or 3 from `spec.md`.
- Every task names the exact file or files it changes or verifies.

## Phase 1: Project Setup

**Purpose**: Establish the Python 3.12 package and test environment without adding unapproved
dependencies.

- [ ] T001 Create `pyproject.toml` with Python `>=3.12,<3.13`, runtime dependencies `httpx` and `aiohttp`, test extras `pytest` and `pytest-asyncio`, a `src` package layout, and explicit pytest-asyncio auto mode with function-scoped test and fixture loops.
- [ ] T002 Create the package entry scaffolding in `src/distributed_http_aggregator/__init__.py` and `src/distributed_http_aggregator/__main__.py`, with `__main__.py` delegating to the future CLI entry function and containing no business logic.

**Checkpoint**: `python -m pip install -e ".[test]"` succeeds under Python 3.12 and pytest starts with
the configured asynchronous test mode, even though feature tests are not implemented yet.

---

## Phase 2: Foundational Components

**Purpose**: Define shared immutable domain values, aggregate validation, serialization, and test
helpers that block every user story.

**CRITICAL**: Complete this phase before starting any user-story implementation.

### Tests first

- [ ] T003 [P] Write failing frozen-model and invariant tests for all enums, `WorkerConfiguration`, `SimulatedWorkerConfiguration`, `RunConfiguration`, `ValidationError`, `WorkerOutcome`, `AggregatedDataItem`, `AggregationReport`, and `AggregationCancelled` in `tests/unit/test_models.py`.
- [ ] T004 [P] Write failing table-driven collect-all validation tests for worker count, identity, URL, GET-only method, positive timeout, and concurrency bounds in `tests/unit/test_validation.py` (FR-001, FR-002, FR-021, FR-022, SC-007).
- [ ] T005 [P] Write failing JSON serialization tests in `tests/unit/test_serialization.py` that assert the report contract, stable configured order, success data including `null`, unsuccessful `data: null`, finite non-negative durations, and structured validation errors (FR-012–FR-016, SC-001, SC-009).

### Shared implementation

- [ ] T006 Implement frozen, slotted enums and configuration records in `src/distributed_http_aggregator/models.py`, enforcing verbatim constraints: `worker_id` is "Trimmed, non-empty, unique within the run; maximum 64 characters"; `url` is an "Absolute http or https URL; host required; no embedded credentials"; `method` is "Exactly GET for this assignment"; `timeout_ms` is "Greater than 0"; simulated `delay_ms` is "Greater than or equal to 0"; and `listen_port` is "0 for OS assignment or 1–65535 for standalone use" (depends on T003).
- [ ] T007 Implement `ValidationError`, immutable `WorkerOutcome`, `AggregatedDataItem`, `AggregationReport`, and `AggregationCancelled` in `src/distributed_http_aggregator/models.py`, enforcing `duration_ms` as finite and non-negative, successful `data` as returned data including `null`, unsuccessful `data` as `null`, one outcome per worker, ordered successful data, and an empty successful-data list for all-failure reports (depends on T006).
- [ ] T008 Implement stable collect-all preflight validation in `src/distributed_http_aggregator/validation.py`, including 3–5 workers, unique identities, absolute credential-free HTTP(S) URLs, GET-only requests, positive timeouts, and `1 <= concurrency_limit <= worker_count`, with no I/O on rejection (depends on T004, T006, T007; FR-001, FR-002, FR-021, FR-022).
- [ ] T009 [P] Implement contract-shaped JSON conversion and UTF-8 stdout/file writing helpers in `src/distributed_http_aggregator/serialization.py`, always emitting the seven required outcome fields and rejecting non-finite durations without adding a schema-validation dependency (depends on T005, T007; FR-012–FR-016).
- [ ] T010 Create reusable sample configurations, monotonic timing tolerance helper `max(0.15 seconds, 25% of the longest configured delay or timeout)`, and leak-check helpers in `tests/conftest.py` (depends on T006, T007).

**Checkpoint**: `tests/unit/test_models.py`, `tests/unit/test_validation.py`, and
`tests/unit/test_serialization.py` pass; invalid configurations produce all detectable errors before
any I/O-capable component is invoked.

---

## Phase 3: User Story 1 — Aggregate Successful Worker Results (Priority: P1) MVP

**Goal**: Contact three to five successful local workers concurrently and return one deterministic,
correctly associated JSON report whose duration reflects the longest worker rather than summed
delays.

**Independent Test**: Run three workers with distinct successful data and delays, verify exactly one
ordered outcome/data item per worker, then run the simultaneous scenario 20 times and compare a
controlled concurrent run with a sequential baseline.

### Tests first

- [ ] T011 [P] [US1] Write failing `GET /work` and `GET /health` worker contract tests for success, empty/`null` data, asynchronous delay, OS-assigned loopback ports, and runner cleanup in `tests/integration/test_worker_contract.py` (FR-003, SC-008).
- [ ] T012 [P] [US1] Write failing successful-client tests using `httpx.MockTransport` for identity matching, valid arbitrary JSON data, `null` data, and monotonic per-worker duration in `tests/unit/test_client_mapping.py` (FR-005, FR-013, FR-014).
- [ ] T013 [P] [US1] Write failing real-loopback integration tests for three and five differently delayed workers, concurrent start, configured-order outcomes, exact source association, and total duration in `tests/integration/test_concurrent_aggregation.py` (FR-004, FR-005, FR-012–FR-018, SC-001, SC-003, SC-009).
- [ ] T014 [P] [US1] Write the failing controlled concurrent-versus-sequential benchmark with 0.15/0.30/0.45-second delays and diagnostic timing output in `tests/performance/test_concurrent_vs_sequential.py`, asserting concurrent duration is at most longest delay plus tolerance and below 75% of the measured sequential baseline (FR-018, SC-004).
- [ ] T015 [P] [US1] Write a failing 20-complete-run simultaneous-response reliability test in `tests/integration/test_simultaneous_completion.py`, asserting exact count, configured order, identity, status, payload association, and absence of stale state on every iteration (FR-016, FR-017, FR-020, SC-002).
- [ ] T016 [P] [US1] Write failing CLI contract tests for `list-scenarios`, successful `demo`, standalone `worker` readiness, JSON stdout/file separation, and exit code 0 in `tests/integration/test_cli.py` (SC-008, SC-009).

### Implementation

- [ ] T017 [P] [US1] Implement successful and delayed `aiohttp.web` workers, `GET /work`, `GET /health`, `AppRunner`/`TCPSite` startup on `127.0.0.1`, OS-assigned-port discovery, and reverse-order `WorkerFleet` cleanup in `src/distributed_http_aggregator/worker.py` (depends on T011; FR-003).
- [ ] T018 [P] [US1] Implement one successful worker request in `src/distributed_http_aggregator/client.py` using a provided shared `httpx.AsyncClient`, whole-operation `asyncio.timeout`, finite HTTPX phase timeouts, response identity/shape checks, and `time.perf_counter_ns()` latency measurement (depends on T012; FR-005, FR-006, FR-013, FR-014).
- [ ] T019 [US1] Implement structured aggregation in `src/distributed_http_aggregator/aggregator.py` with one `asyncio.TaskGroup` task per worker, semaphore backpressure, one scoped shared client, task handles retained in configured order, immutable task results, derived successful data, and total monotonic duration (depends on T013, T017, T018; FR-004–FR-007, FR-012–FR-019).
- [ ] T020 [US1] Define deterministic `normal` and `simultaneous` scenario records in `src/distributed_http_aggregator/scenarios.py`, ensuring assignment timing scenarios set concurrency equal to worker count and contain no random behavior (depends on T017, T019; FR-003, FR-020).
- [ ] T021 [US1] Implement `list-scenarios`, successful `demo`, and standalone `worker` command paths in `src/distributed_http_aggregator/cli.py` and wire `src/distributed_http_aggregator/__main__.py` through `asyncio.run()`, JSON-only stdout, diagnostics-only stderr, output-file support, and cleanup on Ctrl+C (depends on T016–T020).
- [ ] T022 [US1] Run the US1 unit, worker-contract, concurrent-aggregation, 20-run reliability, CLI, and performance tests and record commands, measured timing, and pass/fail evidence in `specs/001-async-result-aggregation/verification.md` (depends on T011–T021; SC-001, SC-002, SC-004, SC-008, SC-009).

**Checkpoint**: User Story 1 is independently demonstrable through `demo --scenario normal`; the
20-run race test and concurrent-versus-sequential comparison pass within documented tolerances.

---

## Phase 4: User Story 2 — Preserve Partial Results During Worker Failures (Priority: P2)

**Goal**: Isolate HTTP, transport, response, timeout, and cancellation failures so every healthy
worker remains visible and all resources are cleaned.

**Independent Test**: Combine successful workers with HTTP 500, unavailable, dropped/malformed, and
timed-out workers; verify stable per-worker statuses/reasons and retained success data, then cancel
an active run and verify prompt cleanup and partial cancellation outcomes.

### Tests first

- [ ] T023 [P] [US2] Extend `tests/unit/test_client_mapping.py` with failing mapping tests for HTTP 500, connection refusal, generic network failure, remote protocol failure, invalid JSON, invalid shape, worker identity mismatch, HTTPX timeout, and outer deadline timeout, including unsuccessful `data: null` (FR-006–FR-010, FR-014, SC-003, SC-005).
- [ ] T024 [P] [US2] Extend `tests/integration/test_worker_contract.py` with failing HTTP 500 and malformed-response behavior tests plus delayed-handler cancellation and runner cleanup assertions (FR-003, FR-008, FR-009).
- [ ] T025 [P] [US2] Write failing mixed HTTP 500, unavailable/connection-failure, malformed-response, and success isolation tests in `tests/integration/test_failure_isolation.py`, asserting all configured workers appear once and successful data is retained (FR-008–FR-014, SC-001, SC-003).
- [ ] T026 [P] [US2] Write failing independent timeout-boundary tests in `tests/integration/test_timeouts.py`, verifying one slow worker times out within configured timeout plus tolerance while siblings complete and overall duration remains bounded (FR-006, FR-007, FR-019, SC-005).
- [ ] T027 [P] [US2] Write failing cancellation tests in `tests/integration/test_cancellation_cleanup.py` that cancel an active run, assert an `AggregationCancelled` partial ordered report with outstanding `cancelled` outcomes and `data: null`, and verify client, task, and server cleanup (FR-024).
- [ ] T028 [P] [US2] Write failing diagnostic redaction tests in `tests/unit/test_logging.py` that permit worker identity, duration, status, and stable failure code but reject authorization values, cookies, embedded URL credentials, response bodies, and raw exception dumps (FR-023).

### Implementation

- [ ] T029 [P] [US2] Add fixed `http_500` and `malformed` worker behaviors plus cancellation-safe delayed handlers to `src/distributed_http_aggregator/worker.py`, preserving the published worker API and always propagating `CancelledError` after cleanup (depends on T024; FR-003, FR-009).
- [ ] T030 [P] [US2] Implement explicit exception-to-outcome mapping and sanitized stable failure codes in `src/distributed_http_aggregator/client.py`, mapping status, connect/network/protocol, timeout, JSON/shape, and identity failures while re-raising cancellation (depends on T023; FR-006–FR-010, FR-014).
- [ ] T031 [US2] Add parent-cancellation handling to `src/distributed_http_aggregator/aggregator.py`: cancel and await siblings, close the shared client, retain settled outcomes, mark outstanding workers `cancelled` with `data: null`, and propagate `AggregationCancelled` with `complete=false` partial report (depends on T027, T030; FR-024).
- [ ] T032 [US2] Define deterministic `http-500`, `unavailable`, `timeout`, and `partial-failure` scenarios in `src/distributed_http_aggregator/scenarios.py`, leaving the unavailable endpoint unstarted and using explicit delays/timeouts (depends on T025, T026, T029–T031; FR-020).
- [ ] T033 [US2] Add sanitized structured diagnostics around worker terminal outcomes and total aggregation duration in `src/distributed_http_aggregator/client.py` and `src/distributed_http_aggregator/aggregator.py`, with no sensitive payload or credential fields (depends on T028, T030, T031; FR-023).
- [ ] T034 [US2] Extend `demo` dispatch and report emission for partial-failure scenarios in `src/distributed_http_aggregator/cli.py`, preserving exit code 0 for completed reports containing remote failures and keeping diagnostics on stderr (depends on T032, T033).
- [ ] T035 [US2] Run all US2 mapping, worker, failure-isolation, timeout, cancellation, redaction, and CLI tests and append resource-cleanup and partial-result evidence to `specs/001-async-result-aggregation/verification.md` (depends on T023–T034; SC-003, SC-005, SC-008, SC-009).

**Checkpoint**: Every supported remote failure becomes one isolated worker outcome, successful data
survives mixed failures, timeouts are independent, and cancellation leaves no owned client, server,
or task resources.

---

## Phase 5: User Story 3 — Diagnose Complete Failure and Invalid Configuration (Priority: P3)

**Goal**: Return a valid empty-success report when every worker fails and reject invalid
configuration with all field errors before any request starts.

**Independent Test**: Run an all-failure scenario and verify one failure outcome per worker with
exit code 0; submit a configuration containing every listed invalid class and verify exit code 2,
all field errors, empty stdout, and zero request attempts.

### Tests first

- [ ] T036 [P] [US3] Write a failing all-failure/all-timeout integration test in `tests/integration/test_complete_failure.py`, asserting a complete report, empty `aggregated_data`, exactly one failure outcome with `data: null` per configured worker, bounded completion, and no unhandled exception (FR-008, FR-012, FR-014, FR-019, SC-006).
- [ ] T037 [P] [US3] Write a failing invalid-configuration integration test with a request-start spy in `tests/integration/test_invalid_configuration.py`, covering fewer than 3/more than 5 workers, missing/duplicate/over-64-character identities, invalid or credential-bearing URLs, unsupported methods, non-positive timeouts, and invalid concurrency limits (FR-001, FR-002, FR-021, FR-022, SC-007).
- [ ] T038 [P] [US3] Extend `tests/integration/test_cli.py` with failing `aggregate` and all-fail CLI tests for JSON config loading, structured stderr errors, empty stdout on validation failure, output-file behavior, exit code 2 for invalid configuration, and exit code 0 for total remote failure (SC-006, SC-007, SC-009).

### Implementation

- [ ] T039 [P] [US3] Define deterministic `all-fail` scenario composition in `src/distributed_http_aggregator/scenarios.py`, combining supported failure/timeout outcomes without random behavior (depends on T036; FR-020).
- [ ] T040 [US3] Implement `aggregate --config` JSON loading, collect-all validation rejection, `all-fail` demo dispatch, output routing, and documented exit-code behavior in `src/distributed_http_aggregator/cli.py` (depends on T037–T039; FR-021, FR-022).
- [ ] T041 [US3] Create contract-valid `examples/run-normal.json`, `examples/run-partial-failure.json`, and `examples/run-standalone.json` plus deliberately invalid `examples/invalid-run.json` covering multiple validation classes (depends on T040; SC-007, SC-008).
- [ ] T042 [US3] Run the complete-failure, invalid-configuration, CLI, and example-file tests and append exit-code, no-I/O-on-rejection, and report evidence to `specs/001-async-result-aggregation/verification.md` (depends on T036–T041; SC-006, SC-007, SC-009).

**Checkpoint**: User Story 3 independently distinguishes valid total remote failure from local
configuration rejection and never starts network work for invalid input.

---

## Phase 6: Cross-Story Integration and Verification

**Purpose**: Prove the complete system and published contracts across all user stories.

- [ ] T043 [P] Add contract-consistency tests in `tests/integration/test_contracts.py` that validate example inputs against the documented required fields and validate emitted reports against every invariant in `contracts/aggregation-report.schema.json` without introducing another runtime dependency.
- [ ] T044 [P] Add one parametrized end-to-end matrix for all-success, HTTP 500, unavailable, timeout, simultaneous completion, partial failure, total failure, and invalid configuration in `tests/integration/test_acceptance_scenarios.py`, asserting the observable acceptance criteria from `spec.md` (FR-001–FR-024, SC-001–SC-009).
- [ ] T045 [P] Add repeated lifecycle verification in `tests/integration/test_resource_cleanup.py` that runs successful, failed, timed-out, and cancelled aggregations and asserts no owned pending tasks, open clients, or worker sockets remain after each case.
- [ ] T046 Run `python -m pytest tests/unit tests/integration` and `python -m pytest tests/performance -v`, then record commands, environment, measured timings, 20-run results, and failures or passes in `specs/001-async-result-aggregation/verification.md` (depends on T043–T045).
- [ ] T047 Audit FR-001 through FR-024 and SC-001 through SC-009 against implemented source and named automated tests, recording any gap and its resolution in `specs/001-async-result-aggregation/verification.md` (depends on T046).
- [ ] T048 Audit `pyproject.toml` for Python 3.12 and only constitution-approved dependencies, then run package installation and import smoke checks and record the result in `specs/001-async-result-aggregation/verification.md` (depends on T001–T047).

**Checkpoint**: Every functional requirement, success criterion, acceptance scenario, contract, and
resource-cleanup obligation has passing automated evidence.

---

## Phase 7: Documentation and Final Quality Gate

**Purpose**: Make setup, operation, output interpretation, and compliance reproducible for grading.

- [ ] T049 [P] Write `README.md` with assignment purpose, architecture summary, Python 3.12 PowerShell setup, test commands, CLI commands, scenario descriptions, report interpretation, and links to `specs/001-async-result-aggregation/` contracts.
- [ ] T050 [P] Reconcile implemented commands, filenames, exit codes, and expected outcomes with `specs/001-async-result-aggregation/quickstart.md`, changing only documentation where implementation follows the approved contracts.
- [ ] T051 Execute every PowerShell command in `specs/001-async-result-aggregation/quickstart.md` from a clean virtual environment and append reproducibility results and any environment assumptions to `specs/001-async-result-aggregation/verification.md` (depends on T049, T050).
- [ ] T052 Perform the final constitution v1.1.0 review for non-blocking I/O, bounded concurrency, deterministic results, failure isolation, cleanup, sanitized diagnostics, approved dependencies, and complete automated tests; record the signed-off checklist in `specs/001-async-result-aggregation/verification.md` (depends on T051).

**Final Checkpoint**: A clean Windows PowerShell environment can install, test, and demonstrate all
required scenarios exactly as documented, with no constitutional exception.

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts immediately.
- **Phase 2 — Foundation**: Depends on T001–T002 and blocks every user story.
- **Phase 3 — US1**: Depends on T003–T010; delivers the MVP.
- **Phase 4 — US2**: Depends on the foundational phase and integrates with US1 transport,
  aggregation, worker, scenario, and CLI components. Its tests can be authored while US1 is being
  completed, but T029–T034 depend on the named US1 files being implemented.
- **Phase 5 — US3**: Depends on the foundational phase; all-failure implementation builds on US2
  failure mapping, and CLI validation builds on the shared validator.
- **Phase 6 — Integration/verification**: Depends on all selected user stories.
- **Phase 7 — Documentation/final gate**: Depends on full integration verification.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 (MVP)
                    -> US2 -> US3 all-failure path
                    -> US3 validation path
US1 + US2 + US3 -> Cross-story verification -> Documentation/final gate
```

### Within Each Story

1. Author the story's tests and confirm they fail for missing behavior.
2. Implement worker/client/domain behavior before orchestration that consumes it.
3. Implement orchestration before scenario and CLI integration.
4. Run the named independent test set and record checkpoint evidence.
5. Do not advance past a checkpoint with failing tests or leaked owned resources.

## Requirement and Success-Criterion Traceability

| Requirement | Implementation tasks | Verification tasks |
|---|---|---|
| FR-001 | T008 | T004, T037, T044, T047 |
| FR-002 | T006, T008 | T004, T037, T044, T047 |
| FR-003 | T017, T020, T029, T032 | T011, T024, T044, T047 |
| FR-004 | T019 | T013, T014, T044, T047 |
| FR-005 | T018, T019 | T012–T014, T044, T047 |
| FR-006 | T018, T030 | T023, T026, T044, T047 |
| FR-007 | T019, T030 | T026, T044, T047 |
| FR-008 | T007, T030, T031 | T023–T027, T036, T044, T047 |
| FR-009 | T029, T030 | T023–T025, T044, T047 |
| FR-010 | T030 | T023, T025, T044, T047 |
| FR-011 | T019, T030 | T025, T044, T047 |
| FR-012 | T007, T009, T019 | T005, T013, T036, T043, T044, T047 |
| FR-013 | T007, T018, T019 | T012, T013, T025, T043, T044, T047 |
| FR-014 | T007, T009, T030 | T005, T023, T025, T036, T043, T044, T047 |
| FR-015 | T007, T019 | T005, T013, T043, T044, T047 |
| FR-016 | T019 | T005, T013, T015, T043, T044, T047 |
| FR-017 | T019 | T015, T044, T047 |
| FR-018 | T019 | T013, T014, T044, T047 |
| FR-019 | T019, T030 | T026, T036, T044, T047 |
| FR-020 | T020, T032, T039 | T015, T022, T035, T042, T044, T047 |
| FR-021 | T008, T040 | T004, T037, T038, T044, T047 |
| FR-022 | T008, T040 | T004, T037, T038, T044, T047 |
| FR-023 | T033 | T028, T044, T047 |
| FR-024 | T031 | T027, T045, T047 |
| SC-001 | T007, T019, T030 | T013, T015, T025, T036, T044, T047 |
| SC-002 | T019, T020 | T015, T022, T046, T047 |
| SC-003 | T019, T030 | T023, T025, T035, T044, T047 |
| SC-004 | T019, T020 | T014, T022, T046, T047 |
| SC-005 | T030, T032 | T023, T026, T035, T044, T047 |
| SC-006 | T039, T040 | T036, T038, T042, T044, T047 |
| SC-007 | T008, T040, T041 | T004, T037, T038, T042, T044, T047 |
| SC-008 | T017, T020, T032, T039 | T011, T016, T022, T035, T042, T044, T051 |
| SC-009 | T007, T009, T021, T034, T040 | T005, T013, T016, T038, T043, T044, T047 |

## Parallel Opportunities

- T003–T005 can be authored concurrently after setup because they target separate unit-test files.
- T011–T016 can be authored concurrently after the foundation checkpoint.
- Within US1, T017 and T018 can proceed concurrently before T019 integrates them.
- T023–T028 can be authored concurrently; T029 and T030 can then proceed concurrently.
- T036–T038 can be authored concurrently after the required earlier story interfaces stabilize.
- T043–T045 can run in parallel after all user-story checkpoints.
- T049 and T050 can run in parallel after verification.

## Parallel Execution Examples

### User Story 1

```text
Parallel test work: T011, T012, T013, T014, T015, T016
Parallel implementation after tests fail: T017 and T018
Join: T019 -> T020 -> T021 -> T022
```

### User Story 2

```text
Parallel test work: T023, T024, T025, T026, T027, T028
Parallel implementation after tests fail: T029 and T030
Join: T031 -> T032 -> T033 -> T034 -> T035
```

### User Story 3

```text
Parallel test work: T036, T037, T038
Implementation: T039 -> T040 -> T041 -> T042
```

## Implementation Strategy

### MVP First

1. Complete T001–T010 for setup and foundation.
2. Complete T011–T021 for User Story 1.
3. Stop and execute T022.
4. Demonstrate `normal` and `simultaneous` scenarios only after the MVP checkpoint passes.

### Incremental Delivery

1. **Foundation**: Immutable contracts, validation, and JSON serialization.
2. **US1 MVP**: Successful concurrent workers, deterministic report, 20-run reliability, and timing
   proof.
3. **US2 reliability**: Failure isolation, timeouts, cancellation, cleanup, and redaction.
4. **US3 diagnosis**: Complete failure and invalid configuration behavior.
5. **System proof**: Full acceptance matrix, traceability audit, reproducibility, and final
   constitution review.

## Notes

- Do not add a dependency unless its purpose and inability to use the approved stack are documented
  and the constitution is amended or an approved exception is recorded.
- Do not use blocking sleeps, sync-over-async calls, shared mutable result lists, public network
  endpoints, automatic retries, or wall-clock timing in request paths or tests.
- Keep correctness assertions exact; use timing tolerance only in dedicated timeout/performance
  tests.
- Commit after each task or small dependency-complete group, and preserve checkpoint evidence in
  `specs/001-async-result-aggregation/verification.md`.
