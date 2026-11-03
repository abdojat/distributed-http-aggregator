# Phase 0 Research: Asynchronous HTTP Client & Result Aggregator

## Scope and Resolution

All technical-context questions are resolved. Research used the constitution-approved stack and
official project or Python documentation. No additional runtime or test dependency is justified.

## Decision 1: Structured concurrency with one task per worker

**Decision**: Use `asyncio.TaskGroup`. Create tasks in configured worker order. Each task converts
anticipated remote failures into a `WorkerOutcome`; unexpected defects remain exceptional and cause
TaskGroup to cancel and await siblings.

**Rationale**: TaskGroup provides stronger cleanup guarantees than unstructured tasks. Reading task
results in creation order supplies deterministic output without shared-list mutation.

**Alternatives considered**:

- `asyncio.gather`: preserves input order but does not cancel siblings when one child raises an
  unexpected exception.
- Completion-order collection: useful for streaming but conflicts with deterministic configured
  order and introduces shared-state synchronization.

**Sources**: [Python Task Groups](https://docs.python.org/3.12/library/asyncio-task.html#task-groups),
[Python gather](https://docs.python.org/3.12/library/asyncio-task.html#asyncio.gather)

## Decision 2: One scoped asynchronous HTTP client per run

**Decision**: Create one `httpx.AsyncClient` with `async with`, reuse it for every worker task, and
configure connection limits at or above the validated concurrency limit.

**Rationale**: This enables connection pooling, centralizes resource ownership, and guarantees client
closure after success, failure, or cancellation.

**Alternatives considered**:

- One client per request: wastes connection pools and complicates cleanup.
- Global process-wide client: obscures ownership and can leak across tests or event loops.

**Source**: [HTTPX async support](https://www.python-httpx.org/async/)

## Decision 3: Independent end-to-end deadlines

**Decision**: Wrap each complete worker operation in `asyncio.timeout()` and also use finite
`httpx.Timeout` values for connect, read, write, and pool phases. Map both timeout families to the
same stable `timeout` outcome.

**Rationale**: Transport timeouts bound individual I/O phases; the outer deadline bounds the complete
operation including connection-pool wait, body read, parsing, and contract validation.

**Alternatives considered**:

- HTTP transport timeout only: does not express one hard whole-operation deadline as clearly.
- Outer timeout only: loses phase-specific protection and diagnostics from the HTTP client.

**Sources**: [HTTPX timeouts](https://www.python-httpx.org/advanced/timeouts/),
[Python timeout contexts](https://docs.python.org/3.12/library/asyncio-task.html#timeouts)

## Decision 4: Explicit exception-to-outcome mapping

**Decision**: Map HTTP status errors, connection/protocol failures, timeout exceptions, and
response-contract failures to immutable domain outcomes. Do not suppress `CancelledError`; clean up
and propagate cancellation. Unexpected programming exceptions remain visible.

**Rationale**: Expected distributed failures become report data and cannot cancel healthy siblings,
while implementation defects are not misreported as remote failures.

**Alternatives considered**:

- Catching `Exception` and emitting a generic failure: conceals defects and weakens diagnostics.
- Allowing all transport exceptions to escape: causes fail-fast loss of partial results.

**Sources**: [HTTPX exception hierarchy](https://www.python-httpx.org/exceptions/),
[Python task cancellation](https://docs.python.org/3.12/library/asyncio-task.html#task-cancellation)

## Decision 5: Validate worker JSON with standard-library logic

**Decision**: Check status before parsing success data, parse JSON, require an object with
`worker_id` and `data`, and verify the returned identity matches the configured worker. Permit
`data: null`.

**Rationale**: JSON parsing validates syntax, not contract shape. The small fixed contract does not
justify a schema-validation runtime dependency.

**Alternatives considered**:

- Pydantic or a JSON Schema validator: capable but adds an unjustified third-party dependency.
- Accepting arbitrary JSON: cannot identify malformed or misassociated responses reliably.

**Sources**: [HTTPX JSON responses](https://www.python-httpx.org/quickstart/#json-response-content),
[HTTPX status handling](https://www.python-httpx.org/quickstart/#response-status-codes)

## Decision 6: Independent lifecycle-managed worker sites

**Decision**: Represent each worker as its own `aiohttp.web.Application`, `AppRunner`, and `TCPSite`
bound to `127.0.0.1`. Use `GET /work`, non-blocking `asyncio.sleep()` delays, JSON responses, and an
async context manager that always invokes runner cleanup.

**Rationale**: Independent sites provide real HTTP boundaries and independent behavior without
platform-specific process supervision. Explicit lifecycle ownership prevents socket leaks.

**Alternatives considered**:

- `web.run_app()` for all workers: takes over the event loop and does not compose cleanly with the
  client and multiple sites.
- Mandatory OS subprocesses: add Windows supervision and readiness complexity without improving
  the required HTTP isolation. Standalone worker commands still permit a process-per-worker demo.

**Sources**: [aiohttp web reference](https://docs.aiohttp.org/en/stable/web_reference.html),
[aiohttp web quickstart](https://docs.aiohttp.org/en/stable/web_quickstart.html)

## Decision 7: OS-assigned loopback ports

**Decision**: Bind test and default-demo sites to port `0`, then read their assigned addresses after
startup. Bind the address explicitly to `127.0.0.1`.

**Rationale**: This avoids fixed-port conflicts, port pre-probe races, IPv4/IPv6 ambiguity, and
Windows `TIME_WAIT` failures during repeated tests.

**Alternatives considered**:

- Fixed ports: simple for manual standalone commands but fragile for automated or parallel runs.
- Preselecting a free port: contains a time-of-check/time-of-use race.

**Sources**: [aiohttp testing utilities](https://docs.aiohttp.org/en/stable/testing.html),
[Python socket documentation](https://docs.python.org/3.12/library/socket.html)

## Decision 8: Monotonic high-resolution duration measurement

**Decision**: Use `time.perf_counter_ns()` at the worker-operation and whole-run boundaries. Store
elapsed durations, not wall-clock timestamps, and convert to milliseconds for reports.

**Rationale**: The counter is monotonic and high resolution on Python 3.12, including on Windows;
wall-clock adjustment cannot create negative or discontinuous durations.

**Alternatives considered**:

- `time.time()`: can change due to clock synchronization.
- Worker-reported durations: cannot measure client-observed network and parsing time.

**Source**: [Python time documentation](https://docs.python.org/3.12/library/time.html#time.perf_counter_ns)

## Decision 9: Isolated asynchronous tests and deliberate timing margins

**Decision**: Configure `pytest-asyncio` auto mode and explicit function-scoped test/fixture loops.
Use async-generator fixtures, controlled local services, relational timing assertions, a shared
documented tolerance helper, and separate correctness from performance tests.

**Rationale**: Function isolation and guaranteed teardown prevent state leakage. Relational
assertions with deliberately separated expected durations prove concurrency without relying on
exact scheduler timing.

**Alternatives considered**:

- Session-scoped servers: faster but increase state and event-loop coupling.
- Exact-duration assertions: brittle on Windows and loaded development machines.
- Adding `pytest-aiohttp`: convenient but unnecessary because project fixtures can manage runners.

**Sources**: [pytest-asyncio concepts](https://pytest-asyncio.readthedocs.io/en/stable/concepts.html),
[pytest-asyncio configuration](https://pytest-asyncio.readthedocs.io/en/stable/reference/configuration.html),
[pytest flaky-test guidance](https://docs.pytest.org/en/stable/explanation/flaky.html)

## Decision 10: Windows-native execution and packaging

**Decision**: Use `asyncio.run()` for CLI entry points, the default Python 3.12 Windows event-loop
implementation, standard `venv`, and an installable `pyproject.toml`. Document both activation and
direct-interpreter PowerShell commands.

**Rationale**: Standard runners own loop shutdown and Ctrl+C handling. Direct interpreter commands
work even where PowerShell activation scripts are restricted.

**Alternatives considered**:

- Manual loop-policy replacement or Unix signal handlers: platform-specific and unnecessary.
- Ad-hoc `PYTHONPATH` execution: less reproducible than an editable package installation.

**Sources**: [Python asyncio runners](https://docs.python.org/3.12/library/asyncio-runner.html),
[Python platform support](https://docs.python.org/3.12/library/asyncio-platforms.html),
[Python venv](https://docs.python.org/3.12/library/venv.html),
[Python packaging guide](https://packaging.python.org/en/latest/tutorials/installing-packages/)
