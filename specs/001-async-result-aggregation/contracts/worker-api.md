# Worker HTTP Contract

## Scope

Each simulated worker is an independent loopback HTTP server. The worker behavior is fixed when the
server starts so a scenario is repeatable; clients cannot alter delays or outcomes through a request.

## `GET /work`

Starts one simulated unit of work.

### Request

- Method: `GET`
- Authentication: none for the local assignment
- Request body: none
- Query parameters: none
- Content negotiation: client accepts JSON

Methods other than `GET` are outside the assignment client contract. The server may return the
standard method-not-allowed response.

### Successful response

- Status: `200 OK`
- Content-Type: `application/json`

```json
{
  "worker_id": "worker-a",
  "data": {
    "value": 42
  }
}
```

`data` may be any JSON value, including `null`. The client accepts it only when `worker_id` exactly
matches the configured worker identity.

### Simulated worker error

- Status: `500 Internal Server Error`
- Content-Type: `application/json`

```json
{
  "worker_id": "worker-b",
  "error": {
    "code": "simulated_failure",
    "message": "Configured worker failure"
  }
}
```

The client classifies by HTTP status and does not copy the remote body into logs or the report.

### Malformed-response behavior

For the invalid-response edge case, a controlled worker returns `200` with invalid JSON or a JSON
object that lacks the required fields. This behavior is for fault injection only.

### Delay behavior

The Jetty worker schedules its configured non-negative delay without blocking a request thread. A
client disconnect or server shutdown cancels scheduled completion; the handler does not suppress
cancellation.

## `GET /health`

Used only to confirm standalone worker readiness.

- Status: `200 OK`
- Content-Type: `application/json`

```json
{
  "worker_id": "worker-a",
  "status": "ready"
}
```

Health checks do not consume or modify work behavior.

## Contract validation

A successful response is invalid when JSON parsing fails, the top-level value is not an object,
`worker_id` or `data` is absent, or the returned identity differs from configuration. An invalid
response produces an `invalid_response` worker outcome and never contributes aggregated data.
