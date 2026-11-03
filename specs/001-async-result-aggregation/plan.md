# Implementation Plan: Asynchronous HTTP Client & Result Aggregator

**Branch**: `001-async-result-aggregation` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-async-result-aggregation/spec.md`

## Summary

Reimplement the existing behavior in Java 21 as a Maven command-line application. The client uses
one shared `java.net.http.HttpClient` and `sendAsync` to create one `CompletableFuture` pipeline per
worker. A bounded executor limits active work, `CompletableFuture.allOf` supplies the final
coordination boundary, and results are read in configured order. Embedded Jetty 12 servers simulate
independent loopback workers, Jackson 2.x preserves the existing JSON contracts, and JUnit 5 proves
the functional and timing requirements. The Python prototype remains untouched and is not part of
the Java build.

## Technical Context

**Language/Version**: Java 21
**Build**: Apache Maven; compiler `release` 21; Maven Wrapper recommended for reproducibility
**Primary Dependencies**: Java `HttpClient.sendAsync`, `CompletableFuture`, Eclipse Jetty 12,
Jackson 2.x
**Testing**: JUnit 5, Maven Surefire, controlled embedded Jetty servers; no public network
**Storage**: None; JSON configuration input and report output only
**Target Platform**: Windows 10/11 PowerShell with a Java 21 JDK; portable to Java 21 platforms
**Project Type**: Single Maven application containing CLI, client/aggregator, and embedded workers
**Performance Goals**: Preserve SC-004 and SC-005 timing bounds and the 20-run SC-002 reliability test
**Constraints**: Three to five workers; bounded configurable concurrency; deterministic configured
order; finite per-request deadlines; no blocking in request-processing paths; no public internet
**Scale/Scope**: One process, at most five loopback worker servers, one request per worker per run

## Constitution Check

| Gate | Design evidence | Pre-design | Post-design |
|---|---|---:|---:|
| Java-only implementation | Maven compiles only Java paths; Python remains historical | PASS | PASS |
| Non-blocking outbound HTTP | Every call uses `sendAsync`; composition uses `CompletableFuture` | PASS | PASS |
| Bounded resources | Dedicated bounded executor, finite deadlines, explicit shutdown | PASS | PASS |
| Deterministic aggregation | Future handles retained in configured order; immutable records | PASS | PASS |
| Failure isolation | Expected remote failures map to outcomes; siblings remain independent | PASS | PASS |
| Approved stack | Java 21, Maven, JUnit 5, Jetty 12, Jackson 2.x | PASS | PASS |
| Repeatable integration tests | Loopback-only embedded workers and fixed scenarios | PASS | PASS |
| Sanitized diagnostics | Stable codes and metadata only; no secrets, bodies, or raw exceptions | PASS | PASS |

No constitutional exception is required.

## Project Structure

### Documentation

```text
specs/001-async-result-aggregation/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- verification.md
|-- contracts/
`-- tasks.md
```

### Java source

```text
pom.xml
.mvn/wrapper/
mvnw
mvnw.cmd
src/main/java/edu/university/aggregator/
|-- Main.java
|-- cli/AggregatorCli.java
|-- config/RunConfigurationParser.java
|-- config/RunConfigurationValidator.java
|-- model/                         # records and enums
|-- json/JsonCodec.java
|-- client/WorkerHttpClient.java
|-- service/AggregationService.java
|-- scenario/ScenarioCatalog.java
`-- worker/                        # Jetty worker and fleet lifecycle
src/test/java/edu/university/aggregator/
|-- unit/
|-- integration/
`-- performance/
examples/
```

**Structure decision**: Use a conventional single-module Maven layout. Packages separate immutable
domain data, validation, transport, orchestration, worker simulation, scenarios, and CLI concerns.
The existing Python `src/`, `tests/`, and `pyproject.toml` remain untouched during migration and are
excluded from Maven compilation.

## Architecture and Responsibilities

1. `AggregatorCli` parses commands and reads UTF-8 JSON through `JsonCodec`.
2. `RunConfigurationParser` rejects unknown fields; the validator collects all semantic errors
   before any network operation.
3. `ScenarioCatalog` creates fixed worker/client configurations for demonstrations.
4. `WorkerFleet` starts one independent embedded Jetty server per available worker on loopback and
   discovers OS-assigned ports.
5. `AggregationService` creates one worker future immediately for every configuration, while a
   bounded executor enforces the concurrency limit.
6. `WorkerHttpClient` calls `HttpClient.sendAsync`, applies an independent timeout, validates the
   response, and maps expected failures to immutable `WorkerOutcome` records.
7. `CompletableFuture.allOf` is the sole normal completion barrier. After it completes, the service
   reads already-completed results in configured order, derives `aggregated_data`, and measures the
   total duration with `System.nanoTime()`.
8. Jackson writes the unchanged JSON report envelope to stdout or the selected file. Diagnostics
   use stderr only.

## Concurrency and Cancellation

- Create one future pipeline per worker without waiting for earlier workers.
- Use one shared immutable-configured `HttpClient` per application lifecycle and a bounded executor
  sized from `concurrency_limit`.
- Use `orTimeout` (or an equivalent scheduled timeout completed exceptionally) for the complete
  request/parse/validation operation. Cancel underlying transport work where possible.
- Expected failures are recovered inside each worker pipeline. Unexpected defects remain visible.
- Parent cancellation calls `cancel(true)` on outstanding futures, retains settled results, and
  supplies `cancelled` outcomes for outstanding workers.
- Blocking `get()` or `join()` is prohibited in client and worker pipelines. Reading completed
  futures after `allOf` at the service boundary is permitted and documented.

## HTTP, JSON, and Failure Contracts

The JSON schemas in `contracts/` remain canonical. Jackson MUST reject unknown input properties,
preserve documented property names, emit UTF-8 JSON, and reject non-finite durations. The worker API
remains `GET /work` and `GET /health`.

| Condition | Status | Failure code |
|---|---|---|
| 2xx and valid worker JSON | `success` | `null` |
| non-2xx HTTP response | `http_error` | `http_status` |
| connection establishment failure | `connection_failure` | `connect_error` |
| other network I/O failure | `connection_failure` | `network_error` |
| invalid protocol response | `connection_failure` | `remote_protocol_error` |
| independent deadline expires | `timeout` | `request_timeout` |
| malformed JSON | `invalid_response` | `invalid_json` |
| missing/wrong fields | `invalid_response` | `invalid_shape` |
| response identity mismatch | `invalid_response` | `worker_identity_mismatch` |
| parent aggregation cancelled | `cancelled` | `cancelled` |

## Worker Lifecycle and Cleanup

Each worker owns an embedded Jetty server bound to `127.0.0.1`. A scheduled executor completes
delayed responses without sleeping a request thread. Fleet startup is all-or-cleanup: on failure,
already-started servers stop in reverse order. Every terminal path closes response bodies, cancels
scheduled work, stops Jetty servers, and shuts down owned executors. Standalone workers use a JVM
shutdown hook plus an explicit close path.

## Testing Strategy

- **Unit**: records/invariants, collect-all validation, Jackson shape, failure mapping, redaction,
  scenario determinism.
- **Integration**: real loopback Jetty workers, concurrent ordering, HTTP 500, unavailable endpoint,
  malformed response, independent timeout, cancellation, CLI streams/exit codes, cleanup.
- **Performance/reliability**: controlled 0.15/0.30/0.45-second delays, sequential baseline, SC-004
  thresholds, and 20 simultaneous-completion repetitions.
- **Contract**: JSON examples and reports checked against every documented schema invariant.
- **Historical evidence**: `verification.md` proves only the Python prototype. Every Java task and
  criterion requires new JUnit/Maven evidence.

## Acceptance Traceability

| Story | Primary requirements | Java verification focus |
|---|---|---|
| US1 | FR-003–FR-007, FR-012–FR-020 | `sendAsync`, timing, ordering, 20-run reliability |
| US2 | FR-006–FR-014, FR-023–FR-024 | mappings, timeout, cancellation, redaction, cleanup |
| US3 | FR-001–FR-002, FR-008, FR-012, FR-019–FR-022 | all-failure report, validation, no-I/O rejection |

All FR-001–FR-024 and SC-001–SC-009 retain explicit task coverage in `tasks.md`.

## Local Execution Approach

1. Verify `java -version` reports 21 and `mvn -version` uses that JDK.
2. Run `mvn clean verify` (or `mvnw.cmd clean verify` once the wrapper exists).
3. Run the packaged application with `java -jar target/distributed-http-aggregator.jar ...`.
4. Record Java-only commands, versions, timings, and results in `verification.md`.

## Complexity Tracking

| Choice | Why required | Simpler option rejected because |
|---|---|---|
| Embedded Jetty 12 | Mandatory suitable server and independent lifecycle | JDK server does not meet the selected library constraint |
| Jackson 2.x | Stable strict JSON mapping | Hand-written JSON risks contract drift |
| Dedicated bounded executor | Explicit concurrency and cleanup | Common-pool use obscures limits and isolation |
