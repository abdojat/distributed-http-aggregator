# Quickstart and Validation Guide

This guide describes the planned Java 21 implementation. Until the Java tasks are implemented,
commands below are expected to fail because no Java application exists yet. The Python prototype is
intentionally retained but is not valid evidence for this guide.

## Prerequisites

- Java Development Kit 21 (`java -version` and `javac -version`)
- Apache Maven 3.9+ (`mvn -version`) or the Maven Wrapper once created
- Windows PowerShell from the repository root
- Loopback ports available for OS assignment

## Build and Automated Validation

```powershell
java -version
mvn -version
mvn clean verify
```

Expected after implementation: Maven compiles with release 21, JUnit 5 unit/integration/performance
checks pass, and an executable JAR is created under `target/`. Timing-sensitive tests must not run
in parallel with other performance workloads.

To run groups during development:

```powershell
mvn -Dtest='**/unit/**' test
mvn -Dtest='**/integration/**' test
mvn -Dtest='**/performance/**' test
```

## Inspect Available Demonstrations

```powershell
java -jar target/distributed-http-aggregator.jar list-scenarios
```

Expected names: `normal`, `http-500`, `unavailable`, `timeout`, `simultaneous`,
`partial-failure`, and `all-fail`.

## Validate Required Scenarios

```powershell
java -jar target/distributed-http-aggregator.jar demo --scenario normal
java -jar target/distributed-http-aggregator.jar demo --scenario http-500
java -jar target/distributed-http-aggregator.jar demo --scenario unavailable
java -jar target/distributed-http-aggregator.jar demo --scenario timeout
java -jar target/distributed-http-aggregator.jar demo --scenario simultaneous
java -jar target/distributed-http-aggregator.jar demo --scenario partial-failure
java -jar target/distributed-http-aggregator.jar demo --scenario all-fail
```

Expected behavior, in order:

- `normal`: all workers succeed in configured order; total duration is near the longest delay.
- `http-500`: one `http_error` with status 500; successful data remains.
- `unavailable`: one `connection_failure`; successful siblings remain.
- `timeout`: one independent `timeout` near its configured boundary.
- `simultaneous`: every worker appears exactly once with correct association and order.
- `partial-failure`: successes remain and each failure has its own status, duration, and reason.
- `all-fail`: exit 0 with a complete report, empty data, and one failure per worker.

Run invalid configuration validation:

```powershell
java -jar target/distributed-http-aggregator.jar aggregate --config examples\invalid-run.json
```

Expected: exit 2, all detectable field errors on stderr, empty stdout, and no request attempt.

## Write and Inspect a Report

```powershell
java -jar target/distributed-http-aggregator.jar demo --scenario partial-failure --output report.json
Get-Content .\report.json
```

Compare with [aggregation-report.schema.json](contracts/aggregation-report.schema.json). The report
must contain total duration, ordered worker outcomes, source-associated successful data, and failure
details.

## Run Standalone Workers

Open three PowerShell windows and choose distinct ports:

```powershell
java -jar target/distributed-http-aggregator.jar worker --id worker-a --host 127.0.0.1 --port 8101 --delay-ms 150 --behavior success --data-json '{"value":1}'
```

Repeat for `worker-b` and `worker-c` with ports 8102 and 8103. In another window:

```powershell
java -jar target/distributed-http-aggregator.jar aggregate --config .\examples\run-normal.json
```

Ctrl+C must stop Jetty and owned executors before exit. Prefer `demo` for grading because it uses
OS-assigned ports and automatic cleanup.

## Java Verification Record

After implementation, append to `verification.md`:

- exact `java` and Maven versions;
- pinned Jetty, Jackson, JUnit, and Maven plugin versions;
- `mvn clean verify` result and test counts;
- 20-run simultaneous-completion result;
- concurrent, sequential, timeout, and tolerance measurements;
- lifecycle/cleanup evidence and FR/SC audit.

Do not count the preserved Python batches as Java verification.

## Contract References

- [Worker HTTP contract](contracts/worker-api.md)
- [Run configuration schema](contracts/run-configuration.schema.json)
- [Aggregation report schema](contracts/aggregation-report.schema.json)
- [CLI contract](contracts/cli.md)
- [Data model](data-model.md)
