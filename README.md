# Distributed HTTP Aggregator

## Assignment purpose and final project

This repository is a university distributed-systems assignment demonstrating concurrent HTTP
requests to independent workers, deterministic result aggregation, and failure isolation. The
selected project option is the asynchronous HTTP client and result aggregator.

The **final deliverable is the Java 21 implementation** in `src/main/java`, verified by Maven and
JUnit.

## Technology stack

- Java 21 (`java.net.http.HttpClient`)
- Apache Maven with the included Maven Wrapper (`mvnw.cmd`)
- `CompletableFuture` and `HttpClient.sendAsync()` for asynchronous execution
- Eclipse Jetty 12 for controlled embedded loopback workers
- Jackson 2.x for strict JSON parsing and serialization
- JUnit 5, Maven Surefire, and Maven Shade

Versions are pinned in [pom.xml](pom.xml) and recorded in
[verification.md](specs/001-async-result-aggregation/verification.md).

## Architecture

1. `AggregatorCli` parses `list-scenarios`, `demo`, `aggregate`, and `worker` commands and keeps
   reports on stdout while sending diagnostics to stderr.
2. `JsonCodec` and `RunConfigurationValidator` strictly parse contracts, reject unknown fields,
   and collect configuration errors before any request starts.
3. `ScenarioCatalog` defines repeatable demonstrations.
4. `WorkerFleet` and `WorkerServer` start independent Jetty workers on loopback ports and clean
   them up in reverse order.
5. `AggregationService` creates one future per configured worker and uses `AsyncPermitPool` to
   enforce the configured concurrency limit.
6. `WorkerHttpClient` validates responses and maps HTTP, transport, protocol, timeout, and
   cancellation conditions to one immutable `WorkerOutcome` per worker.
7. `AggregationReport` preserves configured worker order and derives successful
   `aggregated_data` only from successful outcomes.

Each operation is started without waiting for a previous worker. `WorkerHttpClient` uses
`HttpClient.sendAsync()` and composes `CompletableFuture` pipelines with independent deadlines.
`CompletableFuture.allOf()` is the final coordination boundary; only after it completes does the
service read already-completed futures in configured order. The bounded executor, permit pool,
timeouts, cancellation propagation, and try-with-resources cleanup prevent unbounded work and
resource leaks.

## Windows PowerShell setup and build

Install JDK 21 and ensure `java` is on `PATH`:

```powershell
java -version
.\mvnw.cmd -version
```

The wrapper provisions Maven 3.9.9 on first use; network access is required to download Maven and
pinned dependencies. From the repository root:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd package -DskipTests
```

The executable artifact is `target\distributed-http-aggregator.jar`.

## Demonstrations

Run all demonstrations from the repository root:

```powershell
java -jar target\distributed-http-aggregator.jar list-scenarios
java -jar target\distributed-http-aggregator.jar demo --scenario normal
java -jar target\distributed-http-aggregator.jar demo --scenario http-500
java -jar target\distributed-http-aggregator.jar demo --scenario unavailable
java -jar target\distributed-http-aggregator.jar demo --scenario timeout
java -jar target\distributed-http-aggregator.jar demo --scenario simultaneous
java -jar target\distributed-http-aggregator.jar demo --scenario partial-failure
java -jar target\distributed-http-aggregator.jar demo --scenario all-fail
java -jar target\distributed-http-aggregator.jar aggregate --config examples\invalid-run.json
```

Valid remote failures still exit `0` because they produce a complete report. Invalid local
configuration exits `2`, writes a structured error envelope to stderr, and writes no report to
stdout. Unexpected local failures exit `1`.

For a standalone report, start three workers in separate PowerShell windows using
`examples\run-standalone.json`'s ports, then run:

```powershell
java -jar target\distributed-http-aggregator.jar worker --id standalone-a --host 127.0.0.1 --port 9001 --delay-ms 150 --behavior success
java -jar target\distributed-http-aggregator.jar worker --id standalone-b --host 127.0.0.1 --port 9002 --delay-ms 150 --behavior success
java -jar target\distributed-http-aggregator.jar worker --id standalone-c --host 127.0.0.1 --port 9003 --delay-ms 150 --behavior success
java -jar target\distributed-http-aggregator.jar aggregate --config examples\run-standalone.json
```

Prefer `demo` for grading because it assigns loopback ports automatically and cleans up all owned
workers. Ctrl+C stops a standalone worker through its shutdown hook.

## Reports and diagnostics

Reports are JSON documents conforming to
[aggregation-report.schema.json](specs/001-async-result-aggregation/contracts/aggregation-report.schema.json).
They contain total duration, `complete`, one ordered outcome per configured worker, and
`aggregated_data` entries associated with their source worker. Successful workers may explicitly
return null data; failures retain stable status, failure code, reason, and duration. Diagnostics
contain only worker identity, stable codes/statuses, and duration; credentials, headers, cookies,
response bodies, and raw exception details are not emitted.

Write a report to a file and inspect it with:

```powershell
java -jar target\distributed-http-aggregator.jar demo --scenario partial-failure --output report.json
Get-Content .\report.json
```

## Automated tests and performance verification

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Dtest='**/unit/**' test
.\mvnw.cmd -Dtest='**/integration/**' test
.\mvnw.cmd -Dtest='**/performance/**' test
```

The test suite uses only controlled loopback Jetty workers. It covers model and JSON contracts,
validation-before-I/O, asynchronous concurrency, timeout classification, cancellation races,
failure isolation, diagnostic redaction, cleanup, CLI exit codes, all scenarios, and the
performance/reliability thresholds.

The final performance verification measured `0.462817` seconds for concurrent execution versus
`0.949622` seconds for the sequential baseline, a `2.052x` speedup.

## Engineering Reflection

SDD guided the work by making the contracts, architecture, acceptance criteria, and verification
evidence explicit before implementation. Specification analysis clarified exit codes, scenario
names, ordering, and report shape before coding.

Tests found defects that were easy to miss in a demonstration: timeout classification at the
deadline boundary and a cancellation race that could lose settled outcomes or leave owned
resources active. Coding-agent outputs were supervised through task dependencies, contract tests,
focused integration tests, clean builds, executable-JAR smoke tests, and the final constitution
audit rather than being accepted without verification.

## Specification and evidence links

- [Constitution v2.0.0](.specify/memory/constitution.md)
- [Feature specification](specs/001-async-result-aggregation/spec.md)
- [Implementation plan](specs/001-async-result-aggregation/plan.md)
- [Task list](specs/001-async-result-aggregation/tasks.md)
- [Quickstart and validation guide](specs/001-async-result-aggregation/quickstart.md)
- [Verification evidence](specs/001-async-result-aggregation/verification.md)
- [Worker HTTP contract](specs/001-async-result-aggregation/contracts/worker-api.md)
- [Run configuration schema](specs/001-async-result-aggregation/contracts/run-configuration.schema.json)
- [CLI contract](specs/001-async-result-aggregation/contracts/cli.md)
