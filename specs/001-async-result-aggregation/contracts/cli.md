# Command-Line Interface Contract

## General behavior

- Entry point: `java -jar target/distributed-http-aggregator.jar <command>`
- Machine-readable command results go to stdout or an explicitly selected file.
- Human-readable diagnostics and validation messages go to stderr.
- Paths may be relative to the current directory or absolute Windows paths.
- Commands execute under Java 21. Outbound work uses `HttpClient.sendAsync` and
  `CompletableFuture` composition.

## Exit codes

| Code | Meaning |
|---:|---|
| 0 | Command completed; for aggregation this includes partial or total remote failure reported in valid JSON |
| 1 | Unexpected internal or local I/O failure |
| 2 | CLI or run-configuration validation failed before requests started |
| 130 | User interrupted the command after resource cleanup, where the platform exposes this convention |

## `list-scenarios`

```text
java -jar target/distributed-http-aggregator.jar list-scenarios
```

Prints stable scenario names and brief descriptions. Required names are `normal`, `http-500`,
`unavailable`, `timeout`, `simultaneous`, `partial-failure`, and `all-fail`.

## `demo`

```text
java -jar target/distributed-http-aggregator.jar demo --scenario <name> [--output <report.json>]
```

Starts the scenario's independent loopback workers, waits for readiness, performs one aggregation,
writes a contract-valid report, and cleans all runners. `unavailable` intentionally leaves one
configured endpoint without a listener. `--output` writes UTF-8 JSON to the chosen file; otherwise
the JSON report is stdout.

## `aggregate`

```text
java -jar target/distributed-http-aggregator.jar aggregate --config <run.json> [--output <report.json>]
```

Loads the published run-configuration contract, validates every field before network I/O, contacts
already-running workers, and writes one aggregation report. Validation errors are emitted together
as structured JSON on stderr and use exit code 2.

The validation-error JSON document has this exact envelope and preserves stable document/field
order:

```json
{
  "errors": [
    {
      "field": "workers[1].timeout_ms",
      "code": "out_of_range",
      "message": "timeout_ms must be greater than 0"
    }
  ]
}
```

`errors` is a non-empty array containing every detected error. Each entry has exactly the string
fields `field`, `code`, and `message`; additional fields are not permitted.

## `worker`

```text
java -jar target/distributed-http-aggregator.jar worker --id <id> --host 127.0.0.1 --port <port> --delay-ms <n> --behavior <success|http-500|malformed> [--data-json <json>]
```

Starts one standalone local worker. `--port 0` requests an OS-assigned port and prints one readiness
record containing the actual URL before waiting. Ctrl+C initiates runner cleanup before exit.
`--data-json` is accepted only for success behavior and must parse as a JSON value.

For PowerShell, a JSON file is preferred over complex inline JSON quoting when payloads are nested.

## Diagnostics and secrecy

Diagnostic records may contain worker identity, stable status/failure code, and elapsed duration.
They must not contain authorization headers, cookies, embedded URL credentials, or response bodies.
