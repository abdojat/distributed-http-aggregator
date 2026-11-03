<!--
Sync Impact Report
- Version change: 1.1.0 -> 2.0.0
- Modified principles: none
- Modified sections:
  - Assignment Constraints (replaced the Python stack with the mandatory Java stack)
- Added sections: none
- Removed sections: none
- Follow-up TODOs: none
-->
# Asynchronous HTTP Client & Result Aggregator Constitution

## Core Principles

### I. Non-Blocking Asynchronous Execution
All outbound HTTP operations MUST use asynchronous, non-blocking I/O. The system MUST permit
independent requests to make progress concurrently and MUST NOT serialize requests unless an
explicit dependency requires ordering. Blocking waits, thread sleeps used for coordination, and
sync-over-async patterns are prohibited in request-processing paths. This rule ensures the project
demonstrates the distributed-systems concurrency model named in the assignment.

### II. Bounded Concurrency and Resource Safety
Concurrency MUST be explicitly bounded and configurable. Connections, tasks, response bodies, and
other resources MUST be released on success, failure, cancellation, and timeout. The client MUST
reuse HTTP connection infrastructure where the selected platform supports it and MUST apply
backpressure when work exceeds the configured concurrency limit. These constraints prevent
resource exhaustion and make behavior predictable under load.

### III. Deterministic Result Aggregation
Every submitted request MUST produce exactly one identifiable aggregation outcome, including
successful responses, HTTP errors, transport failures, timeouts, and cancellations. Aggregated
results MUST use a documented schema and MUST preserve a stable association with the originating
request. Ordering semantics MUST be explicit and tested; when input order is promised, completion
timing MUST NOT alter output order. Partial success MUST remain visible rather than being discarded
because another request failed.

### IV. Failure Isolation and Explicit Resilience
A failure in one remote call MUST NOT prevent unrelated calls from completing or their results from
being reported. Each request MUST have a finite timeout, and cancellation MUST propagate promptly
through the asynchronous operation. Retries, if implemented, MUST be bounded, limited to failures
that are safe to retry, and use delay or backoff; retry behavior MUST NOT conceal the final attempt
history. The aggregator MUST represent failures as data or documented domain errors, not as
unhandled process-level failures.

### V. Verification and Observability
Core scheduling, aggregation, timeout, cancellation, and error-mapping behavior MUST be covered by
automated tests. Integration tests MUST exercise concurrent HTTP interactions using controlled test
servers or deterministic substitutes and MUST include mixed success and failure scenarios. Runtime
diagnostics MUST identify requests without exposing secrets and MUST record durations and terminal
outcomes. Tests MUST be repeatable and MUST NOT depend on public internet availability.

## Assignment Constraints

- The project MUST be implemented exclusively in Java 21. Python artifacts MAY remain only as
  historical prototype evidence and MUST NOT be part of the submitted Java runtime or build.
- Asynchronous concurrency MUST use `CompletableFuture`; outbound request paths MUST remain
  non-blocking and MUST NOT call `join()` or `get()` until the final orchestration boundary where
  all independently progressing operations are collected.
- Outbound HTTP operations MUST use the Java 21 `java.net.http.HttpClient` API and MUST invoke
  `HttpClient.sendAsync`; `send` is prohibited in request-processing paths.
- The build and dependency-management system MUST be Apache Maven, with a compiler release of 21.
- Automated tests MUST use JUnit 5. Integration tests MUST use controlled loopback servers and MUST
  remain independent of public internet services.
- Simulated HTTP worker servers MUST use Eclipse Jetty 12 as an embedded Java HTTP server. JSON
  parsing and serialization MUST use Jackson 2.x. Their exact compatible versions MUST be pinned
  in `pom.xml` and recorded in verification evidence.
- Java 21 standard-library APIs MAY be used wherever appropriate, including
  `ScheduledExecutorService` for non-blocking simulated delays and `System.nanoTime()` for elapsed
  duration measurement.
- Any additional third-party dependency MUST have a documented justification describing why the
  approved stack or standard library cannot reasonably satisfy the requirement.
- The implementation MUST remain focused on an asynchronous HTTP client and result aggregator;
  unrelated infrastructure or features require written justification.
- Public interfaces and result schemas MUST be documented with their concurrency, ordering,
  timeout, cancellation, and failure semantics.
- Configuration MUST provide safe defaults and MUST validate invalid concurrency limits, timeouts,
  request definitions, and unsupported methods before work begins where practicable.
- Credentials, authorization headers, cookies, and sensitive response data MUST NOT appear in logs
  or committed fixtures.
- External dependencies MUST have a clear purpose, be compatible with the assignment environment,
  and avoid replacing the distributed-systems concepts the submission is expected to demonstrate.
- The repository MUST include concise instructions for setup, execution, testing, and interpretation
  of aggregated output.

## Development Workflow and Quality Gates

1. Each change MUST trace to an assignment requirement, an approved specification, or a documented
   defect.
2. Behavior-affecting work MUST define acceptance examples before or alongside implementation,
   including at least one failure case where applicable.
3. Changes MUST pass formatting and static checks configured by the project, unit tests, and all
   relevant integration tests before they are considered complete.
4. Reviews MUST verify non-blocking behavior, bounded resource use, deterministic aggregation,
   failure isolation, and absence of sensitive logging.
5. Any deliberate exception to a principle MUST be documented with its scope, academic or technical
   justification, risks, and a removal or review condition.
6. Submission artifacts MUST be reproducible from the repository without undocumented manual
   changes.

## Governance

This constitution is the highest-authority project governance document. Specifications, plans,
tasks, implementation decisions, and review practices MUST comply with it. Where another project
document conflicts with this constitution, this constitution governs until formally amended.

Amendments MUST be proposed as a documented change that states the motivation and impact on current
artifacts. Approval requires explicit acceptance by the project owner or assignment team and an
updated Sync Impact Report. Amendments that invalidate existing guarantees or remove principles
require a MAJOR version increment; new principles or materially expanded obligations require a
MINOR increment; clarifications that do not change obligations require a PATCH increment.

Compliance MUST be reviewed when specifications and plans are created, before implementation is
accepted, and before submission. Reviewers MUST record any exception under the workflow above.
Complexity that is not required by the assignment or these principles MUST be removed or justified
in writing.

**Version**: 2.0.0 | **Ratified**: 2026-11-03 | **Last Amended**: 2026-11-03
