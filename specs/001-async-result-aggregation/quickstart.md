# Quickstart and Validation Guide

This guide validates the final Java 21 implementation.

## Prerequisites

- JDK 21 (`java -version`)
- Windows PowerShell from the repository root
- The included Maven Wrapper (`.\mvnw.cmd`); it provisions Maven 3.9.9
- Loopback ports available for OS assignment and standalone workers

## Clean build and automated validation

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean verify
.\mvnw.cmd package -DskipTests
```

The clean build compiles Java release 21, runs all JUnit 5 tests, and creates
`target\distributed-http-aggregator.jar`.

The recorded performance benchmark completed the concurrent run in `0.462817` seconds versus
`0.949622` seconds sequentially, for a `2.052x` speedup.

Focused test groups:

```powershell
.\mvnw.cmd -Dtest='**/unit/**' test
.\mvnw.cmd -Dtest='**/integration/**' test
.\mvnw.cmd -Dtest='**/performance/**' test
```

## Demonstrations

```powershell
java -jar target\distributed-http-aggregator.jar list-scenarios
java -jar target\distributed-http-aggregator.jar demo --scenario normal
java -jar target\distributed-http-aggregator.jar demo --scenario http-500
java -jar target\distributed-http-aggregator.jar demo --scenario unavailable
java -jar target\distributed-http-aggregator.jar demo --scenario timeout
java -jar target\distributed-http-aggregator.jar demo --scenario simultaneous
java -jar target\distributed-http-aggregator.jar demo --scenario partial-failure
java -jar target\distributed-http-aggregator.jar demo --scenario all-fail
```

Expected outcomes are, respectively: seven listed scenarios; three successes; one HTTP error
with successful siblings; one connection failure with successful siblings; independent timeout
classification; five simultaneous successes in configured order; mixed success and failure
outcomes; and five failures with zero aggregated data. Valid remote failures exit `0`.

## Invalid configuration and report output

```powershell
java -jar target\distributed-http-aggregator.jar aggregate --config examples\invalid-run.json
java -jar target\distributed-http-aggregator.jar demo --scenario partial-failure --output report.json
Get-Content .\report.json
```

Invalid configuration exits `2`, emits all detectable errors as structured JSON on stderr, emits
empty stdout, and starts no requests. Reports conform to
[aggregation-report.schema.json](contracts/aggregation-report.schema.json).

## Standalone workers

Open three PowerShell windows and run one command in each:

```powershell
java -jar target\distributed-http-aggregator.jar worker --id standalone-a --host 127.0.0.1 --port 9001 --delay-ms 150 --behavior success
java -jar target\distributed-http-aggregator.jar worker --id standalone-b --host 127.0.0.1 --port 9002 --delay-ms 150 --behavior success
java -jar target\distributed-http-aggregator.jar worker --id standalone-c --host 127.0.0.1 --port 9003 --delay-ms 150 --behavior success
```

In a fourth window:

```powershell
java -jar target\distributed-http-aggregator.jar aggregate --config examples\run-standalone.json
```

Ctrl+C invokes cleanup for each standalone worker. The `demo` command is preferred for grading
because it owns and cleans up all loopback workers automatically.

## Engineering reflection

SDD made the contracts, architecture, acceptance criteria, and verification evidence explicit
before implementation. The final Java design uses asynchronous HTTP futures, bounded concurrency,
independent deadlines, deterministic ordering, failure isolation, and resource cleanup. Testing
exposed deadline-boundary and cancellation-race defects that would not be apparent from a single
successful demonstration.

## Contract references

- [Worker HTTP contract](contracts/worker-api.md)
- [Run configuration schema](contracts/run-configuration.schema.json)
- [Aggregation report schema](contracts/aggregation-report.schema.json)
- [CLI contract](contracts/cli.md)
- [Data model](data-model.md)
