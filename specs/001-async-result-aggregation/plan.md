# Implementation Plan: Asynchronous HTTP Client & Result Aggregator

**Branch**: `master` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-async-result-aggregation/spec.md`

## Summary

Build a Python 3.12 command-line demonstration that starts or contacts three to five independent
local HTTP workers, issues one request per worker concurrently, converts every expected remote
failure into an immutable worker outcome, and serializes one deterministic JSON aggregation report.
The coordinator uses structured asynchronous concurrency, a single scoped HTTP client, independent
deadlines, configured-order result assembly, and monotonic timing. Local workers expose a minimal
HTTP contract and are managed with explicit asynchronous startup and cleanup. Unit, integration,
fault-injection, race, cancellation, and timing tests prove FR-001 through FR-024 and SC-001 through
SC-009 without public network access.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: Python `asyncio`; `httpx.AsyncClient`; `aiohttp.web`; Python standard
library (`dataclasses`, `enum`, `json`, `logging`, `time`, `argparse`, `pathlib`, `urllib.parse`)

**Storage**: No persistent storage; JSON configuration is read from files and JSON reports are
written to standard output or an explicitly selected file

**Testing**: `pytest` and `pytest-asyncio`; `httpx.MockTransport` for focused client unit tests;
real loopback HTTP endpoints for integration and fault-injection tests

**Target Platform**: Windows 10/11 with PowerShell and Python 3.12; design remains portable to other
Python 3.12 platforms

**Project Type**: Single Python package with CLI, asynchronous client/aggregator, and local worker
server components

**Performance Goals**: For three to five workers, concurrent duration is bounded by the longest
active worker or timeout plus a documented tolerance and, in the controlled benchmark, is less than
75% of both the configured delay sum and the measured sequential baseline when the configured delay
sum is at least twice the longest delay

**Constraints**: Three to five workers; finite per-request timeout; bounded configurable
concurrency; deterministic configured-order output; no public internet; no automatic retries;
credentials and sensitive payloads excluded from logs; no third-party dependencies beyond the
constitution-approved stack

**Scale/Scope**: One local aggregation run, three to five independent worker endpoints, one request
per worker, and eight repeatable demonstration scenarios; no persistence, authentication, remote
deployment, or production service management

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Constitutional obligation | Design evidence | Pre-design | Post-design |
|---|---|---|---|
| Non-blocking asynchronous execution | `asyncio.TaskGroup`, async HTTP calls, and async worker delays; no blocking waits in request paths | PASS | PASS |
| Bounded concurrency and resource safety | Validated concurrency limit, semaphore backpressure, one scoped client, worker context managers, cancellation cleanup | PASS | PASS |
| Deterministic aggregation | One immutable outcome per task; tasks retained and read in configured order; JSON contract fixes field semantics | PASS | PASS |
| Failure isolation and resilience | Expected failures are mapped inside each worker task; independent deadlines; no automatic retries; partial successes retained | PASS | PASS |
| Verification and observability | Unit, local integration, failure, race, cancellation, and performance tests; sanitized structured logs | PASS | PASS |
| Mandated technology stack | Python 3.12, `asyncio`, `httpx`, `aiohttp.web`, `pytest`, and `pytest-asyncio` only | PASS | PASS |
| Safe configuration and documented interfaces | Aggregate validation before I/O; JSON schemas, worker HTTP contract, CLI contract, and quickstart | PASS | PASS |
| Reproducible local operation | Loopback-only workers, OS-assigned test ports, PowerShell commands, controlled scenarios | PASS | PASS |

No constitutional violations or exceptions require justification. All research questions are
resolved in [research.md](research.md).

## Project Structure

### Documentation (this feature)

```text
specs/001-async-result-aggregation/
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- contracts/
|   |-- aggregation-report.schema.json
|   |-- cli.md
|   |-- run-configuration.schema.json
|   `-- worker-api.md
`-- tasks.md                         # Future output of $speckit-tasks; not created here
```

### Source Code (repository root)

```text
pyproject.toml
README.md
examples/
|-- run-normal.json
|-- run-partial-failure.json
|-- run-standalone.json
`-- invalid-run.json
src/
`-- distributed_http_aggregator/
    |-- __init__.py
    |-- __main__.py                  # python -m entry point
    |-- cli.py                       # argument parsing and command dispatch
    |-- models.py                    # immutable domain values and enums
    |-- validation.py                # collect-all configuration validation
    |-- client.py                    # one worker HTTP operation and error mapping
    |-- aggregator.py                # task orchestration and ordered report assembly
    |-- serialization.py             # domain-to-JSON conversion
    |-- worker.py                    # aiohttp worker application and lifecycle
    `-- scenarios.py                 # controlled normal/failure scenario definitions
tests/
|-- unit/
|   |-- test_models.py
|   |-- test_validation.py
|   |-- test_client_mapping.py
|   `-- test_aggregation.py
|-- integration/
|   |-- test_worker_contract.py
|   |-- test_concurrent_aggregation.py
|   |-- test_failure_isolation.py
|   |-- test_cancellation_cleanup.py
|   `-- test_simultaneous_completion.py
|-- performance/
|   `-- test_concurrent_vs_sequential.py
`-- conftest.py                      # local worker and client fixtures
```

**Structure Decision**: Use one installable `src`-layout Python package. Domain models, validation,
transport, orchestration, serialization, worker simulation, and CLI remain separate modules, while
tests are grouped by risk. This is the smallest structure that preserves clear client/server and
domain boundaries without introducing services, persistence, or framework layers not required by
the assignment.

## Architecture and Component Responsibilities

### Control Flow

1. `cli` loads a JSON run configuration or a named controlled scenario.
2. `validation` returns all `ValidationError` values; any error prevents server/client work.
3. For `demo`, `scenarios` creates three to five independent loopback worker applications and a
   `WorkerFleet` async context manager starts one runner/site per worker.
4. `aggregator` starts total timing, creates one scoped `httpx.AsyncClient`, and creates one task per
   worker in a single `asyncio.TaskGroup`.
5. Each `client` task applies semaphore backpressure and an independent whole-operation deadline,
   validates the HTTP response, and returns exactly one immutable `WorkerOutcome` for expected
   terminal conditions.
6. After all tasks settle, `aggregator` reads task results in configuration order, derives successful
   aggregated data, records total duration, and creates `AggregationReport`.
7. `serialization` emits contract-valid JSON to stdout or an explicitly requested path.
8. Context-manager exit closes the HTTP client and worker runners even after timeout, cancellation,
   assertion failure, or unexpected exception.

### Components

- **Domain models (`models`)**: Frozen, slotted data classes and string enums. No network or file
  behavior. Constructors enforce local invariants; cross-record checks belong to `validation`.
- **Configuration validator (`validation`)**: Collects all errors in stable field order. It validates
  worker count, identities, URLs, GET-only request definition, positive timeouts, and concurrency
  bounds before any I/O.
- **Worker server (`worker`)**: Builds one `aiohttp.web.Application` per worker. `GET /work` waits
  asynchronously for its configured delay and produces success, HTTP 500, or malformed-response
  behavior. `GET /health` supports readiness only. `AppRunner`/`TCPSite` lifecycle is explicit.
- **Scenario registry (`scenarios`)**: Immutable definitions for normal, HTTP 500, unavailable,
  timeout, simultaneous, partial-failure, and all-failure demonstrations. No random behavior.
- **HTTP operation (`client`)**: Performs one request, validates the response contract, maps expected
  transport/protocol/status/parse failures, measures latency, and never mutates shared state.
- **Aggregation coordinator (`aggregator`)**: Owns concurrency bounds, shared client lifecycle,
  structured task lifecycle, cancellation propagation, ordered assembly, and total timing.
- **Serializer (`serialization`)**: Converts domain values to the published JSON contract without
  leaking exception representations, secrets, or internal objects.
- **CLI (`cli`, `__main__`)**: Provides `demo`, `worker`, `aggregate`, and `list-scenarios` commands;
  maps validation problems to exit code 2 and completed aggregation reports to exit code 0 even
  when remote workers fail.

## Concurrency and State-Management Strategy

- Create one task per configured worker immediately inside `asyncio.TaskGroup`. Each task captures
  its worker configuration and returns one outcome.
- Use an `asyncio.Semaphore(concurrency_limit)` around the request operation. The validated limit is
  between 1 and worker count; assignment timing scenarios set it equal to worker count so all
  requests can be active together.
- Use one `httpx.AsyncClient` per aggregation run with connection limits no lower than the validated
  concurrency bound. Reuse it across tasks and close it through `async with`.
- Wrap the full request, body read, JSON parse, and response validation in
  `asyncio.timeout(timeout_seconds)` and also configure finite HTTPX connect/read/write/pool
  timeouts. Both timeout sources map to `TIMEOUT`.
- Store task handles in configuration order and read each task result in that order after the task
  group exits. Do not append from tasks to a shared list and do not use locks for aggregation.
- Make configurations and outcomes immutable. Derive `aggregated_data` after tasks finish. This
  makes simultaneous completions race-safe by construction.
- On parent cancellation, TaskGroup cancels and awaits children. Worker tasks record sanitized
  cancellation diagnostics in `finally` and re-raise `CancelledError`. The coordinator closes the
  shared client before propagating an `AggregationCancelled` cancellation carrying the partial
  ordered outcomes available at cancellation time; outstanding workers are marked `CANCELLED`.
- Use `time.perf_counter_ns()` for request and aggregation intervals. Convert elapsed nanoseconds to
  non-negative milliseconds only at model/serialization boundaries; never use wall-clock time for
  durations.

## HTTP and Data Contracts

- Worker endpoints and payload semantics are defined in [worker-api.md](contracts/worker-api.md).
- Input configuration is defined by
  [run-configuration.schema.json](contracts/run-configuration.schema.json).
- Output is defined by
  [aggregation-report.schema.json](contracts/aggregation-report.schema.json).
- CLI commands, exit codes, stdout/stderr behavior, and cleanup expectations are defined in
  [cli.md](contracts/cli.md).
- Entity fields, invariants, relationships, and transitions are defined in
  [data-model.md](data-model.md).

The client accepts only `GET` for this assignment. A successful worker response is a JSON object
containing `worker_id` and `data`; `data` may be any JSON value including `null`. The returned
`worker_id` must match the configured identity. Invalid JSON, missing fields, or an identity mismatch
maps to `INVALID_RESPONSE` and never enters aggregated data. A successful outcome always preserves
the observed 2xx HTTP status. Validation failures use the ordered JSON envelope defined in
[cli.md](contracts/cli.md).

## Failure-Handling Strategy

| Condition | Detection | Worker status | Required behavior |
|---|---|---|---|
| HTTP 2xx and valid contract | Status check and response validation | `success` | Preserve data and source identity |
| HTTP 4xx/5xx, including 500 | HTTP status exception | `http_error` | Preserve status code; sanitize reason |
| Connection refused/DNS failure | HTTPX connect/network exception | `connection_failure` | Isolate failure; continue siblings |
| Remote protocol/drop failure | HTTPX protocol/network exception | `connection_failure` | Isolate failure; continue siblings |
| Whole-operation or HTTP timeout | Async deadline or HTTPX timeout | `timeout` | Stop only that request at finite boundary |
| Invalid JSON/shape/worker identity | Parse and contract validation | `invalid_response` | Exclude data; preserve failure reason |
| Parent cancellation | Task cancellation | `cancelled` | Cancel/await siblings, clean resources, propagate cancellation with partial outcomes |
| Invalid run configuration | Preflight validation | No request status | Return all validation errors; start no I/O |
| Unexpected programming defect | Unhandled task exception | Not normalized | TaskGroup cancels siblings; cleanup runs; defect propagates visibly |

Automatic retries are deliberately excluded. They would change timing, obscure attempt history, and
are unnecessary for the assignment. Error messages use stable failure codes plus concise sanitized
text; raw response bodies, authorization headers, cookies, and full exception dumps do not enter the
report or normal logs.

## Worker Lifecycle and Resource Cleanup

- Each worker owns an `Application`, `AppRunner`, and `TCPSite`, bound explicitly to `127.0.0.1`.
- Tests and `demo` request port `0` and discover the OS-assigned port after startup to avoid races
  and Windows `TIME_WAIT` conflicts.
- `WorkerFleet` starts workers in configured order and cleans runners in reverse order in `finally`.
- Delayed handlers use `asyncio.sleep`; they do not suppress cancellation.
- The standalone `worker` command uses `asyncio.run`, performs `runner.cleanup()` on normal exit or
  Ctrl+C cancellation, and does not install Unix-only signal handlers.
- The default `demo` hosts independent HTTP endpoints in one event loop instead of spawning child
  processes. This keeps cleanup deterministic on Windows while preserving real HTTP isolation.
  Separately launched `worker` commands remain independent OS processes when that demonstration is
  desired; the operator stops them with Ctrl+C and each process executes the same cleanup path.
- Client response streams are fully consumed within the request scope, the shared client is closed,
  all child tasks are awaited, and tests assert no owned tasks or sockets remain.

## Testing Strategy

### Test Configuration

- Configure `pytest-asyncio` in auto mode with explicit function-scoped test and fixture event loops.
- Use async-generator fixtures with `try/finally` cleanup for worker runners and clients.
- Use OS-assigned loopback ports and controlled delays; never call public services.
- Keep correctness tests free of narrow timing assertions. Timing tests run separately and report
  observed concurrent, sequential, longest-delay, timeout, and tolerance values on failure.
- Define the timing tolerance in one test helper as `max(0.15 seconds, 25% of the longest configured
  delay or timeout)`. Use performance delays with enough separation that this tolerance cannot make
  concurrent and sequential expectations overlap. Revisit only with recorded evidence.

### Test Groups

- **Unit**: Frozen model invariants, JSON conversion, collect-all validation, ordering assembly, and
  every exception-to-status mapping using `httpx.MockTransport` where real sockets add no value.
- **Worker contract**: Success, empty data, HTTP 500, delay, malformed payload, identity mismatch,
  readiness, and runner cleanup.
- **Integration**: Real client calls to three to five local servers for normal, partial-failure,
  unavailable, timeout, simultaneous, and complete-failure scenarios.
- **Race/repetition**: At least 20 complete runs against controlled equal-delay workers. Every
  iteration asserts exact count, order, identity, status, payload association, and no stale state.
- **Cancellation/cleanup**: Cancel an active run, assert prompt propagation, cancelled outcomes,
  client closure, runner cleanup, and no owned pending tasks.
- **Performance**: Measure concurrent and sequential control runs against the same three workers
  (planned delays 0.15/0.30/0.45 seconds). Assert concurrent time is at most the longest delay plus
  tolerance and less than 75% of both the configured delay sum and sequential baseline. Do not run
  this test under process-parallel test execution.
- **Scenario repeatability**: Execute each of the eight required acceptance scenarios for at least
  two consecutive runs and assert equivalent worker-status classifications or validation-error code
  sequences, as applicable, on every repetition.
- **CLI**: Validate commands, JSON stdout, file output, stderr separation, and exit codes in
  PowerShell-compatible subprocess invocations.

### Acceptance Traceability

| Requirement/outcome | Primary design evidence | Primary verification |
|---|---|---|
| FR-001–FR-003 | Validation, scenario registry, worker contract | Validation and worker-contract tests |
| FR-004–FR-007 | TaskGroup, semaphore, shared async client, independent deadlines | Concurrent and timeout integration tests |
| FR-008–FR-011 | Exception mapping and immutable outcomes | Fault-injection and partial-failure tests |
| FR-012–FR-017 | Report schema and configured-order assembly | Model, aggregation, and simultaneous-completion tests |
| FR-018–FR-020 | Monotonic timing and controlled scenarios | Concurrent-vs-sequential and repeatability tests |
| FR-021–FR-022 | Collect-all preflight validation | Table-driven invalid-configuration tests |
| FR-023 | Sanitized structured diagnostics | Log-capture redaction tests |
| FR-024 | TaskGroup cancellation and context cleanup | Cancellation/cleanup integration tests |
| SC-001, SC-003, SC-006, SC-009 | Full worker/report contracts | Scenario report assertions |
| SC-002 | Immutable outcomes and configured-order assembly | 20-run simultaneous-completion stress test |
| SC-004–SC-005 | Monotonic timing and documented tolerance | Performance and timeout tests |
| SC-007 | Aggregate validation before I/O | Invalid-config test with request-start spy |
| SC-008 | Local deterministic scenario registry | Repeated end-to-end scenario suite |

All eight acceptance scenarios are represented directly: all-success, HTTP 500, unavailable,
timeout, simultaneous completion, partial failure, total failure, and invalid configuration.

## Local Setup and Execution Approach

The quickstart is defined in [quickstart.md](quickstart.md). Planned PowerShell flow:

1. Create a Python 3.12 virtual environment.
2. Install the package and test extras from local project metadata.
3. Run `python -m pytest` for all automated checks.
4. Run `python -m distributed_http_aggregator list-scenarios`.
5. Run `python -m distributed_http_aggregator demo --scenario normal` or another named scenario.
6. Optionally run standalone worker processes in separate PowerShell windows and invoke
   `aggregate --config <path>` against them.

The CLI writes machine-readable JSON only to stdout (or the selected output file) and human-readable
diagnostics only to stderr. A completed report, including all-remote-failure, exits 0; invalid local
configuration exits 2; unexpected internal failure exits 1; user cancellation follows interrupt
semantics after cleanup.

## Design Decisions and Justification

1. **TaskGroup over shared-list/gather orchestration**: provides structured sibling cleanup for
   unexpected defects. Expected failures are converted inside tasks so they do not trigger fail-fast.
2. **Immutable task return values over concurrent mutation**: removes the need for locks and makes
   configured-order assembly deterministic.
3. **One scoped client over one client per worker**: enables connection pooling and central cleanup.
4. **Whole-operation deadline plus finite transport timeouts**: bounds connection acquisition,
   transfer, parsing, and validation, not only individual socket operations.
5. **In-process independent HTTP sites for the default demo**: preserves genuine HTTP boundaries
   with reliable Windows cleanup; standalone processes remain available without burdening tests.
6. **Frozen standard-library data classes over a validation framework**: the domain is small and the
   constitution requires justification for extra dependencies.
7. **OS-assigned ports over fixed or pre-probed ports**: avoids race conditions and repeated-run
   conflicts.
8. **No retries**: preserves repeatability, timeout meaning, and visible failure classification.
9. **JSON contracts and stable error codes**: make reports inspectable and testable across CLI and
   automated scenarios.
10. **Relational timing tests over exact durations**: proves concurrency while tolerating ordinary
    Windows scheduling and local processing variation.

## Complexity Tracking

No constitution violations, unjustified dependencies, or exceptional complexity are present.
