# Quickstart and Validation Guide

This guide describes the intended local workflow after implementation. It validates the contracts
and acceptance scenarios; it does not contain implementation code.

## Prerequisites

- Windows 10 or 11 with PowerShell
- Python 3.12 available through the Python launcher (`py -3.12`) or `python`
- No public network services are required after dependencies are installed

## Environment Setup

From the repository root:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -e ".[test]"
```

If PowerShell execution policy prevents activation, call the environment interpreter directly:

```powershell
.\.venv\Scripts\python.exe -m pip install -e ".[test]"
.\.venv\Scripts\python.exe -m pytest
```

The planned runtime dependencies are `httpx` and `aiohttp`; test dependencies are `pytest` and
`pytest-asyncio`. Any additional dependency requires a documented constitution justification.

## Run Automated Validation

```powershell
python -m pytest
```

Focused groups:

```powershell
python -m pytest tests/unit
python -m pytest tests/integration
python -m pytest tests/performance -v
```

Expected results:

- Unit tests validate configuration, immutable models, serialization, ordered assembly, and error
  mapping.
- Integration tests use three to five local loopback workers and cover all required failure modes.
- The simultaneous-completion test performs at least 20 aggregation runs with exact association and
  ordering checks.
- The performance test prints measured concurrent and sequential durations when an assertion fails
  and proves the concurrent result meets SC-004.
- No test requires a public endpoint or leaves an owned client, task, or server socket open.

Do not run the timing-sensitive performance test through a process-parallel pytest plugin.

## Inspect Available Demonstrations

```powershell
python -m distributed_http_aggregator list-scenarios
```

Expected scenario names: `normal`, `http-500`, `unavailable`, `timeout`, `simultaneous`,
`partial-failure`, and `all-fail`.

## Validate Required Scenarios

### All workers succeed with different delays

```powershell
python -m distributed_http_aggregator demo --scenario normal
```

Expected: one success per worker in configured order, all successful data represented once, and
total duration near the longest delay rather than the sum.

### One worker returns HTTP 500

```powershell
python -m distributed_http_aggregator demo --scenario http-500
```

Expected: the selected worker has status `http_error` and HTTP status 500; all other successful data
remains in `aggregated_data`.

### One worker is unavailable

```powershell
python -m distributed_http_aggregator demo --scenario unavailable
```

Expected: one `connection_failure`, successful siblings retained, and a complete report.

### One worker exceeds its timeout

```powershell
python -m distributed_http_aggregator demo --scenario timeout
```

Expected: one `timeout` near its configured boundary; faster siblings complete normally.

### Multiple workers complete simultaneously

```powershell
python -m distributed_http_aggregator demo --scenario simultaneous
```

Expected: each worker appears exactly once, data associations are correct, and output follows
configured order rather than completion order.

### Some workers fail while others succeed

```powershell
python -m distributed_http_aggregator demo --scenario partial-failure
```

Expected: successful data is retained and every failed worker has its own status, duration, and
reason.

### All workers fail or time out

```powershell
python -m distributed_http_aggregator demo --scenario all-fail
```

Expected: exit code 0 with a complete report, empty `aggregated_data`, and one failure outcome per
worker.

### Invalid configuration

```powershell
python -m distributed_http_aggregator aggregate --config examples\invalid-run.json
```

Expected: exit code 2, all detectable field errors on stderr, no report on stdout, and no request
attempted.

## Write and Inspect a Report

```powershell
python -m distributed_http_aggregator demo --scenario partial-failure --output report.json
Get-Content .\report.json
```

Compare the result with
[aggregation-report.schema.json](contracts/aggregation-report.schema.json). It must show total
duration, ordered worker outcomes, successful data with source identities, and failure details.

## Run Standalone Workers

Open three PowerShell windows and choose distinct ports:

```powershell
python -m distributed_http_aggregator worker --id worker-a --host 127.0.0.1 --port 8101 --delay-ms 150 --behavior success --data-json '{"value":1}'
```

Repeat for `worker-b` and `worker-c` with ports 8102 and 8103. In another window, create a run file
that follows [run-configuration.schema.json](contracts/run-configuration.schema.json), then run:

```powershell
python -m distributed_http_aggregator aggregate --config .\examples\run-normal.json
```

Press Ctrl+C in each worker window. Each worker must close its HTTP runner before exiting. Use the
single-command `demo` flow for routine grading because it avoids fixed-port conflicts and performs
automatic cleanup.

## Contract References

- [Worker HTTP contract](contracts/worker-api.md)
- [Run configuration schema](contracts/run-configuration.schema.json)
- [Aggregation report schema](contracts/aggregation-report.schema.json)
- [CLI contract](contracts/cli.md)
- [Data model](data-model.md)
