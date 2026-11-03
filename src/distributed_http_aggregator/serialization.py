"""JSON serialization for aggregation and validation reports."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, TextIO

from .models import AggregationReport, ValidationErrorReport, WorkerOutcome

JsonDocument = dict[str, Any]


def _worker_outcome_to_document(outcome: WorkerOutcome) -> JsonDocument:
    return {
        "worker_id": outcome.worker_id,
        "status": outcome.status.value,
        "duration_ms": outcome.duration_ms,
        "data": outcome.data,
        "http_status": outcome.http_status,
        "failure_code": (
            outcome.failure_code.value if outcome.failure_code is not None else None
        ),
        "failure_reason": outcome.failure_reason,
    }


def aggregation_report_to_document(report: AggregationReport) -> JsonDocument:
    return {
        "complete": report.complete,
        "total_duration_ms": report.total_duration_ms,
        "worker_outcomes": [
            _worker_outcome_to_document(outcome)
            for outcome in report.worker_outcomes
        ],
        "aggregated_data": [
            {"worker_id": item.worker_id, "data": item.data}
            for item in report.aggregated_data
        ],
    }


def validation_error_report_to_document(
    report: ValidationErrorReport,
) -> JsonDocument:
    return {
        "errors": [
            {"field": error.field, "code": error.code, "message": error.message}
            for error in report.errors
        ]
    }


def _dumps(document: JsonDocument) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False) + "\n"


def dumps_aggregation_report(report: AggregationReport) -> str:
    return _dumps(aggregation_report_to_document(report))


def dumps_validation_errors(report: ValidationErrorReport) -> str:
    return _dumps(validation_error_report_to_document(report))


def _write(text: str, destination: TextIO | str | Path | None) -> None:
    if destination is None:
        sys.stdout.write(text)
        return
    if isinstance(destination, (str, Path)):
        Path(destination).write_text(text, encoding="utf-8")
        return
    destination.write(text)


def write_aggregation_report(
    report: AggregationReport, destination: TextIO | str | Path | None = None
) -> None:
    _write(dumps_aggregation_report(report), destination)


def write_validation_errors(
    report: ValidationErrorReport, destination: TextIO | str | Path | None = None
) -> None:
    _write(dumps_validation_errors(report), destination)
