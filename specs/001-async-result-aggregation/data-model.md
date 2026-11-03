# Data Model: Asynchronous HTTP Client & Result Aggregator

## Modeling Rules

- Domain records are frozen, slotted data classes to prevent mutation after validation.
- Enumerations serialize to the lowercase string values defined here.
- Internal durations are elapsed monotonic nanoseconds; public JSON durations are milliseconds.
- Configuration order is retained as an explicit tuple/list position and is the canonical report
  order.
- Models contain no sockets, clients, tasks, runners, or open files; resource ownership belongs to
  orchestration components.

## Enumerations

### WorkerBehavior

| Value | Meaning |
|---|---|
| `success` | Return HTTP 200 and contract-valid JSON |
| `http_500` | Return a deterministic HTTP 500 error payload |
| `malformed` | Return HTTP 200 with invalid JSON or an invalid success shape |
| `unavailable` | Do not start a server for this configured endpoint |

### WorkerStatus

| Value | Meaning |
|---|---|
| `success` | Valid 2xx response with matching worker identity and accepted data |
| `http_error` | Non-success HTTP status |
| `connection_failure` | Destination could not be reached or protocol connection failed |
| `timeout` | Whole operation or transport phase exceeded its deadline |
| `invalid_response` | Response was not valid according to the worker success contract |
| `cancelled` | Parent aggregation was cancelled before this worker completed |

### FailureCode

The closed set of stable values is `http_status`, `connect_error`, `network_error`,
`remote_protocol_error`, `request_timeout`, `invalid_json`, `invalid_shape`,
`worker_identity_mismatch`, and `cancelled`.

## Entity: WorkerConfiguration

Represents one client-side request target.

| Field | Type | Required | Validation |
|---|---|---:|---|
| `worker_id` | string | yes | Trimmed, non-empty; maximum 64 characters |
| `url` | string | yes | Absolute `http` or `https` URL; host required; no embedded credentials |
| `method` | string | yes | Exactly `GET` for this assignment |
| `timeout_ms` | integer | yes | Greater than 0; recommended local range 50–30,000 ms |

**Invariant**: A validated worker configuration can be paired with exactly one task and one outcome
within a run. This isolated record does not enforce cross-worker uniqueness; `RunConfiguration`
aggregate validation owns that rule.

## Entity: SimulatedWorkerConfiguration

Represents deterministic server behavior for controlled scenarios. It is not sent to the client as
part of the HTTP request.

| Field | Type | Required | Validation |
|---|---|---:|---|
| `worker_id` | string | yes | Same local identity rules as `WorkerConfiguration`; matched to one client configuration by the scenario |
| `behavior` | `WorkerBehavior` | yes | One published behavior value |
| `delay_ms` | integer | yes | Greater than or equal to 0 |
| `data` | any JSON value | for success | May be `null`; must be JSON serializable |
| `listen_host` | string | yes | `127.0.0.1` in controlled scenarios |
| `listen_port` | integer | yes | 0 for OS assignment or 1–65535 for standalone use |

**Relationship**: A started simulated worker yields the URL used by one matching
`WorkerConfiguration`. `unavailable` yields a configuration URL but no running site.

## Entity: RunConfiguration

| Field | Type | Required | Validation |
|---|---|---:|---|
| `workers` | ordered list of `WorkerConfiguration` | yes | Contains 3–5 entries; identities unique |
| `concurrency_limit` | integer | yes | 1 through worker count; assignment performance scenarios require worker count |

**Aggregate validation**: All detectable errors are returned in stable document order. No server or
client I/O starts until the error list is empty.

## Entity: ValidationError

| Field | Type | Required | Rules |
|---|---|---:|---|
| `field` | string | yes | JSON-style path such as `workers[1].timeout_ms` |
| `code` | string | yes | Stable machine-readable validation code |
| `message` | string | yes | Concise actionable text without secrets |

Typical codes: `worker_count`, `required`, `duplicate`, `invalid_url`, `unsupported_method`,
`out_of_range`, and `invalid_type`.

## Entity: ValidationErrorReport

Represents the JSON document written to stderr when configuration validation fails.

| Field | Type | Required | Rules |
|---|---|---:|---|
| `errors` | ordered list of `ValidationError` | yes | Non-empty; stable document/field order; contains all detected errors |

## Entity: WorkerOutcome

| Field | Type | Required | Rules |
|---|---|---:|---|
| `worker_id` | string | yes | Copied from validated configuration |
| `status` | `WorkerStatus` | yes | Exactly one terminal status |
| `duration_ms` | number | yes | Finite and non-negative |
| `data` | any JSON value or null | yes | Successful outcomes preserve returned data, including `null`; unsuccessful outcomes use `null` |
| `http_status` | integer or null | yes | Success requires 200–299; failures preserve 100–599 when supplied, otherwise `null` |
| `failure_code` | string or null | failures only | Stable `FailureCode` value |
| `failure_reason` | string or null | failures only | Sanitized human-readable summary |

**Success invariant**: `status=success`, `http_status` is an integer from 200 through 299, failure
fields are null, and `data` is present even when its value is `null`.

**Failure invariant**: `status!=success`, `data` is `null`, no data enters aggregated data, and
`failure_code` plus `failure_reason` are present. `http_status` is present only when an HTTP response
supplied one.

## Entity: AggregatedDataItem

| Field | Type | Required | Rules |
|---|---|---:|---|
| `worker_id` | string | yes | References a successful outcome |
| `data` | any JSON value | yes | Exact accepted worker data, including `null` |

**Derivation**: Produced from successful outcomes in configured worker order; it is never mutated by
worker tasks directly.

## Entity: AggregationReport

| Field | Type | Required | Rules |
|---|---|---:|---|
| `complete` | boolean | yes | True for normal completion; false only for cancellation diagnostics |
| `total_duration_ms` | number | yes | Finite, non-negative monotonic elapsed duration |
| `worker_outcomes` | ordered list of `WorkerOutcome` | yes | Exactly one per configured worker |
| `aggregated_data` | ordered list of `AggregatedDataItem` | yes | Exactly one per successful outcome |

**Cardinality invariants**:

- Normal completed run: `len(worker_outcomes) == len(config.workers)`.
- `len(aggregated_data) == count(status == success)`.
- Each configured `worker_id` appears exactly once in `worker_outcomes`.
- `worker_outcomes` and `aggregated_data` follow configured worker order.
- An all-failure report is valid with an empty `aggregated_data` list.

## Entity: AggregationCancelled

A documented domain cancellation derived from `asyncio.CancelledError`.

| Field | Type | Required | Rules |
|---|---|---:|---|
| `partial_report` | `AggregationReport` | yes | `complete=false`; settled results retained, outstanding workers marked cancelled |

The exception remains a cancellation signal rather than normal success. Cleanup completes before it
reaches the caller.

## State Transitions

### Worker request

```text
configured -> scheduled -> active -> success
                                 |-> http_error
                                 |-> connection_failure
                                 |-> timeout
                                 |-> invalid_response
                                 `-> cancelled
```

Every scheduled request reaches one terminal status. No terminal status can transition again.

### Aggregation run

```text
loaded -> validating -> rejected
                    `-> running -> completed -> serialized
                              `-> cancelling -> cancelled (partial report attached)
                              `-> internal_error (owned resources cleaned)
```

Validation rejection occurs before any request or worker lifecycle begins.
