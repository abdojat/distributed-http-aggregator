# Phase 0 Research: Java Migration

## Scope and Resolution

All technical questions for the mandatory Java migration are resolved. The behavioral specification
is unchanged. These decisions replace the Python implementation strategy while retaining the JSON
and HTTP contracts.

## Decision 1: Java 21 and Maven

**Decision**: Compile and run exclusively on Java 21 with Apache Maven and conventional Maven source
layout. Pin compatible Jetty, Jackson, JUnit, and plugin versions in `pom.xml`.

**Rationale**: This directly satisfies the university mandate and yields a reproducible workflow.

**Alternatives considered**: Gradle is excluded by mandate. A multi-module layout is unnecessary.

## Decision 2: `HttpClient.sendAsync` plus `CompletableFuture`

**Decision**: Reuse one Java `HttpClient`. Start each request with `sendAsync` and compose response
parsing, validation, timeout, and mapping as a `CompletableFuture<WorkerOutcome>`.

**Rationale**: The Java 21 client supplies non-blocking HTTP and integrates with the mandated future.

**Alternatives considered**: Synchronous `send`, third-party clients, and virtual-thread blocking
calls do not satisfy the explicit requirement.

## Decision 3: Ordered handles and `CompletableFuture.allOf`

**Decision**: Store futures in configured order, compose one `allOf` barrier, then read only
completed results in original order and derive successful data.

**Rationale**: Completion timing cannot alter ordering; no shared result list is required.

**Alternatives considered**: Completion-order queues conflict with FR-016; sequential chaining
violates FR-004 and SC-004.

## Decision 4: Bounded executor and independent deadlines

**Decision**: Give `HttpClient` a dedicated bounded executor sized to the validated concurrency
limit. Apply an independent whole-operation timeout per pipeline and cancel unfinished work.

**Rationale**: The bound is testable and each request has an independent terminal deadline.

**Alternatives considered**: The common pool obscures ownership; one run-wide timeout is inadequate.

## Decision 5: Explicit Java exception mapping

**Decision**: Map known HTTP, connect, timeout, protocol/I/O, Jackson parsing, shape, and identity
failures to the published closed outcome set. Unexpected programming defects remain exceptional.

**Rationale**: Distributed failures become data without concealing implementation faults.

**Alternatives considered**: Catch-all recovery misclassifies defects; uncaught remote exceptions
discard partial results.

## Decision 6: Embedded Jetty 12 workers

**Decision**: Run one independent embedded Jetty 12 server per worker on loopback. Use asynchronous
response handling with scheduled completion for configured delays.

**Rationale**: Jetty suits embedded Java 21 HTTP and offers explicit lifecycle control.

**Alternatives considered**: Spring Boot is excessive. The JDK server is not the selected mandatory
server library.

## Decision 7: Jackson 2.x and unchanged JSON contracts

**Decision**: Use Jackson for strict UTF-8 input and report output. Reject unknown configuration
fields, retain snake_case names, and keep both JSON Schemas unchanged unless Java verification
reveals a genuine behavioral conflict.

**Rationale**: The schemas are technology-independent and already cover required behavior.

**Alternatives considered**: Manual parsing is error-prone; Gson is less direct for strict records.

## Decision 8: Java records and closed enums

**Decision**: Model immutable entities as Java records and closed enums. Validate local invariants
at construction and cross-record invariants in aggregates.

**Rationale**: Value semantics prevent aliasing and cross-run stale state.

**Alternatives considered**: Mutable beans increase synchronization risk.

## Decision 9: Monotonic timing

**Decision**: Measure elapsed durations with `System.nanoTime()` and convert deltas to milliseconds.

**Rationale**: Monotonic elapsed measurement is unaffected by wall-clock adjustment.

**Alternatives considered**: `Instant.now()` is intended for timestamps.

## Decision 10: JUnit 5 controlled tests

**Decision**: Use JUnit 5 for unit, loopback integration, repeated reliability, and controlled
performance tests. Tests own every server and executor. Preserve the tolerance
`max(150 ms, 25% of the longest configured delay or timeout)`.

**Rationale**: JUnit 5 is mandatory and supports deterministic parameterized/repeated tests.

**Alternatives considered**: Mock-only testing cannot prove concurrency and lifecycle behavior.

## Decision 11: CLI without another runtime framework

**Decision**: Implement the four documented commands with a small explicit parser and package an
executable JAR. Preserve stdout/stderr separation and exit codes.

**Rationale**: The command surface is fixed and small.

**Alternatives considered**: A CLI framework adds no essential behavior here.

## Decision 12: Python evidence is historical only

**Decision**: Preserve Python source and verification, exclude them from Maven, and mark every
Python result as non-transferable. Reset all Java implementation and verification tasks.

**Rationale**: The prototype remains useful for comparison but cannot satisfy Java-only submission.

**Alternatives considered**: Deleting or rewriting Python artifacts would lose evidence and violate
the migration request.
