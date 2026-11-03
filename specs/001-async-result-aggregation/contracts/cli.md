# Command-Line Interface Contract

## General behavior

- Entry point: `python -m distributed_http_aggregator <command>`
- Machine-readable command results go to stdout or an explicitly selected file.
- Human-readable diagnostics and validation messages go to stderr.
- Paths may be relative to the current directory or absolute Windows paths.
- Commands execute under Python 3.12 through `asyncio.run()` where asynchronous work is required.

## Exit codes

| Code | Meaning |
|---:|---|
| 0 | Command completed; for aggregation this includes partial or total remote failure reported in valid JSON |
| 1 | Unexpected internal or local I/O failure |
| 2 | CLI or run-configuration validation failed before requests started |
| 130 | User interrupted the command after resource cleanup, where the platform exposes this convention |

## `list-scenarios`

```text
python -m distributed_http_aggregator list-scenarios
```

Prints stable scenario names and brief descriptions. Required names are `normal`, `http-500`,
`unavailable`, `timeout`, `simultaneous`, `partial-failure`, and `all-fail`.

## `demo`

```text
python -m distributed_http_aggregator demo --scenario <name> [--output <report.json>]
```

Starts the scenario's independent loopback workers, waits for readiness, performs one aggregation,
writes a contract-valid report, and cleans all runners. `unavailable` intentionally leaves one
configured endpoint without a listener. `--output` writes UTF-8 JSON to the chosen file; otherwise
the JSON report is stdout.

## `aggregate`

```text
python -m distributed_http_aggregator aggregate --config <run.json> [--output <report.json>]
```

Loads the published run-configuration contract, validates every field before network I/O, contacts
already-running workers, and writes one aggregation report. Validation errors are emitted together
as structured JSON on stderr and use exit code 2.

## `worker`

```text
python -m distributed_http_aggregator worker --id <id> --host 127.0.0.1 --port <port> --delay-ms <n> --behavior <success|http-500|malformed> [--data-json <json>]
```

Starts one standalone local worker. `--port 0` requests an OS-assigned port and prints one readiness
record containing the actual URL before waiting. Ctrl+C initiates runner cleanup before exit.
`--data-json` is accepted only for success behavior and must parse as a JSON value.

For PowerShell, a JSON file is preferred over complex inline JSON quoting when payloads are nested.

## Diagnostics and secrecy

Diagnostic records may contain worker identity, stable status/failure code, and elapsed duration.
They must not contain authorization headers, cookies, embedded URL credentials, or response bodies.
