"""Immutable domain models for configuration and aggregation results."""

from __future__ import annotations

import asyncio
import json
import math
from dataclasses import dataclass
from enum import Enum
from typing import Any, Iterable
from urllib.parse import urlsplit


class WorkerBehavior(str, Enum):
    SUCCESS = "success"
    HTTP_500 = "http_500"
    MALFORMED = "malformed"
    UNAVAILABLE = "unavailable"


class WorkerStatus(str, Enum):
    SUCCESS = "success"
    HTTP_ERROR = "http_error"
    CONNECTION_FAILURE = "connection_failure"
    TIMEOUT = "timeout"
    INVALID_RESPONSE = "invalid_response"
    CANCELLED = "cancelled"


class FailureCode(str, Enum):
    HTTP_STATUS = "http_status"
    CONNECT_ERROR = "connect_error"
    NETWORK_ERROR = "network_error"
    REMOTE_PROTOCOL_ERROR = "remote_protocol_error"
    REQUEST_TIMEOUT = "request_timeout"
    INVALID_JSON = "invalid_json"
    INVALID_SHAPE = "invalid_shape"
    WORKER_IDENTITY_MISMATCH = "worker_identity_mismatch"
    CANCELLED = "cancelled"


def _normalized_worker_id(value: object) -> str:
    if not isinstance(value, str):
        raise TypeError("worker_id must be a string")
    normalized = value.strip()
    if not normalized:
        raise ValueError("worker_id must be non-empty")
    if len(normalized) > 64:
        raise ValueError("worker_id must contain at most 64 characters")
    return normalized


def _validated_url(value: object) -> str:
    if not isinstance(value, str):
        raise TypeError("url must be a string")
    parsed = urlsplit(value)
    if parsed.scheme not in {"http", "https"}:
        raise ValueError("url must use http or https")
    if not parsed.hostname:
        raise ValueError("url must include a host")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("url must not contain embedded credentials")
    return value


def _validated_non_negative_number(value: object, field: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(f"{field} must be a number")
    number = float(value)
    if not math.isfinite(number) or number < 0:
        raise ValueError(f"{field} must be finite and non-negative")
    return number


@dataclass(frozen=True, slots=True)
class WorkerConfiguration:
    worker_id: str
    url: str
    method: str = "GET"
    timeout_ms: int = 1_000

    def __post_init__(self) -> None:
        object.__setattr__(self, "worker_id", _normalized_worker_id(self.worker_id))
        object.__setattr__(self, "url", _validated_url(self.url))
        if not isinstance(self.method, str):
            raise TypeError("method must be a string")
        if self.method != "GET":
            raise ValueError("method must be exactly GET")
        if isinstance(self.timeout_ms, bool) or not isinstance(self.timeout_ms, int):
            raise TypeError("timeout_ms must be an integer")
        if self.timeout_ms <= 0:
            raise ValueError("timeout_ms must be greater than 0")


@dataclass(frozen=True, slots=True)
class SimulatedWorkerConfiguration:
    worker_id: str
    behavior: WorkerBehavior
    delay_ms: int
    data: Any = None
    listen_host: str = "127.0.0.1"
    listen_port: int = 0

    def __post_init__(self) -> None:
        object.__setattr__(self, "worker_id", _normalized_worker_id(self.worker_id))
        if not isinstance(self.behavior, WorkerBehavior):
            raise TypeError("behavior must be a WorkerBehavior")
        if isinstance(self.delay_ms, bool) or not isinstance(self.delay_ms, int):
            raise TypeError("delay_ms must be an integer")
        if self.delay_ms < 0:
            raise ValueError("delay_ms must be greater than or equal to 0")
        if self.listen_host != "127.0.0.1":
            raise ValueError("listen_host must be 127.0.0.1")
        if isinstance(self.listen_port, bool) or not isinstance(self.listen_port, int):
            raise TypeError("listen_port must be an integer")
        if not 0 <= self.listen_port <= 65_535:
            raise ValueError("listen_port must be 0 or between 1 and 65535")
        if self.behavior is WorkerBehavior.SUCCESS:
            try:
                json.dumps(self.data, allow_nan=False)
            except (TypeError, ValueError) as exc:
                raise ValueError("data must be JSON serializable") from exc


@dataclass(frozen=True, slots=True)
class RunConfiguration:
    workers: tuple[WorkerConfiguration, ...]
    concurrency_limit: int

    def __post_init__(self) -> None:
        object.__setattr__(self, "workers", tuple(self.workers))


@dataclass(frozen=True, slots=True)
class ValidationError:
    field: str
    code: str
    message: str

    def __post_init__(self) -> None:
        for name in ("field", "code", "message"):
            value = getattr(self, name)
            if not isinstance(value, str):
                raise TypeError(f"{name} must be a string")
            if not value:
                raise ValueError(f"{name} must be non-empty")


@dataclass(frozen=True, slots=True)
class ValidationErrorReport:
    errors: tuple[ValidationError, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "errors", tuple(self.errors))
        if not self.errors:
            raise ValueError("errors must be non-empty")
        if not all(isinstance(error, ValidationError) for error in self.errors):
            raise TypeError("errors must contain ValidationError values")


@dataclass(frozen=True, slots=True)
class WorkerOutcome:
    worker_id: str
    status: WorkerStatus
    duration_ms: float
    data: Any
    http_status: int | None
    failure_code: FailureCode | None
    failure_reason: str | None

    def __post_init__(self) -> None:
        object.__setattr__(self, "worker_id", _normalized_worker_id(self.worker_id))
        if not isinstance(self.status, WorkerStatus):
            raise TypeError("status must be a WorkerStatus")
        object.__setattr__(
            self,
            "duration_ms",
            _validated_non_negative_number(self.duration_ms, "duration_ms"),
        )
        if self.http_status is not None:
            if isinstance(self.http_status, bool) or not isinstance(self.http_status, int):
                raise TypeError("http_status must be an integer or null")
            if not 100 <= self.http_status <= 599:
                raise ValueError("http_status must be between 100 and 599")

        if self.status is WorkerStatus.SUCCESS:
            if self.http_status is None or not 200 <= self.http_status <= 299:
                raise ValueError("successful outcomes require a 2xx http_status")
            if self.failure_code is not None or self.failure_reason is not None:
                raise ValueError("successful outcomes require null failure fields")
            return

        if self.data is not None:
            raise ValueError("unsuccessful outcomes require null data")
        if not isinstance(self.failure_code, FailureCode):
            raise ValueError("unsuccessful outcomes require a failure_code")
        if not isinstance(self.failure_reason, str) or not self.failure_reason:
            raise ValueError("unsuccessful outcomes require a failure_reason")


@dataclass(frozen=True, slots=True)
class AggregatedDataItem:
    worker_id: str
    data: Any

    def __post_init__(self) -> None:
        object.__setattr__(self, "worker_id", _normalized_worker_id(self.worker_id))


@dataclass(frozen=True, slots=True)
class AggregationReport:
    complete: bool
    total_duration_ms: float
    worker_outcomes: tuple[WorkerOutcome, ...]
    aggregated_data: tuple[AggregatedDataItem, ...]

    def __post_init__(self) -> None:
        if not isinstance(self.complete, bool):
            raise TypeError("complete must be a boolean")
        object.__setattr__(
            self,
            "total_duration_ms",
            _validated_non_negative_number(
                self.total_duration_ms, "total_duration_ms"
            ),
        )
        object.__setattr__(self, "worker_outcomes", tuple(self.worker_outcomes))
        object.__setattr__(self, "aggregated_data", tuple(self.aggregated_data))
        if not all(
            isinstance(outcome, WorkerOutcome) for outcome in self.worker_outcomes
        ):
            raise TypeError("worker_outcomes must contain WorkerOutcome values")
        if not all(
            isinstance(item, AggregatedDataItem) for item in self.aggregated_data
        ):
            raise TypeError("aggregated_data must contain AggregatedDataItem values")

        outcome_ids = [outcome.worker_id for outcome in self.worker_outcomes]
        if len(outcome_ids) != len(set(outcome_ids)):
            raise ValueError("each worker must appear exactly once in worker_outcomes")

        expected_data = tuple(
            AggregatedDataItem(outcome.worker_id, outcome.data)
            for outcome in self.worker_outcomes
            if outcome.status is WorkerStatus.SUCCESS
        )
        if self.aggregated_data != expected_data:
            raise ValueError(
                "aggregated_data must project successful outcomes in configured order"
            )


class AggregationCancelled(asyncio.CancelledError):
    """Cancellation signal carrying the ordered partial aggregation report."""

    __slots__ = ("_partial_report",)

    def __init__(self, partial_report: AggregationReport) -> None:
        if not isinstance(partial_report, AggregationReport):
            raise TypeError("partial_report must be an AggregationReport")
        if partial_report.complete:
            raise ValueError("a cancellation partial_report must be incomplete")
        super().__init__("aggregation cancelled")
        self._partial_report = partial_report

    @property
    def partial_report(self) -> AggregationReport:
        return self._partial_report


def validation_error_report(errors: Iterable[ValidationError]) -> ValidationErrorReport:
    """Build an immutable error report from an iterable without exposing mutation."""

    return ValidationErrorReport(tuple(errors))
