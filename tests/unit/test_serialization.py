import json
from io import StringIO

from distributed_http_aggregator.models import (
    AggregatedDataItem,
    AggregationReport,
    FailureCode,
    ValidationError,
    ValidationErrorReport,
    WorkerOutcome,
    WorkerStatus,
)
from distributed_http_aggregator.serialization import (
    aggregation_report_to_document,
    dumps_aggregation_report,
    dumps_validation_errors,
    validation_error_report_to_document,
    write_aggregation_report,
)


def report() -> AggregationReport:
    success = WorkerOutcome(
        worker_id="worker-a",
        status=WorkerStatus.SUCCESS,
        duration_ms=10.25,
        data=None,
        http_status=204,
        failure_code=None,
        failure_reason=None,
    )
    failed = WorkerOutcome(
        worker_id="worker-b",
        status=WorkerStatus.HTTP_ERROR,
        duration_ms=12.5,
        data=None,
        http_status=500,
        failure_code=FailureCode.HTTP_STATUS,
        failure_reason="HTTP 500",
    )
    return AggregationReport(
        complete=True,
        total_duration_ms=13.0,
        worker_outcomes=(success, failed),
        aggregated_data=(AggregatedDataItem("worker-a", None),),
    )


def test_report_document_matches_published_contract_and_order() -> None:
    document = aggregation_report_to_document(report())

    assert list(document) == [
        "complete",
        "total_duration_ms",
        "worker_outcomes",
        "aggregated_data",
    ]
    assert [outcome["worker_id"] for outcome in document["worker_outcomes"]] == [
        "worker-a",
        "worker-b",
    ]
    assert list(document["worker_outcomes"][0]) == [
        "worker_id",
        "status",
        "duration_ms",
        "data",
        "http_status",
        "failure_code",
        "failure_reason",
    ]
    assert document["worker_outcomes"][0]["data"] is None
    assert document["worker_outcomes"][0]["http_status"] == 204
    assert document["worker_outcomes"][1]["data"] is None
    assert document["worker_outcomes"][1]["failure_code"] == "http_status"
    assert document["aggregated_data"] == [{"worker_id": "worker-a", "data": None}]


def test_report_json_round_trips_as_utf8_json() -> None:
    decoded = json.loads(dumps_aggregation_report(report()))
    assert decoded["worker_outcomes"][1]["failure_reason"] == "HTTP 500"


def test_validation_error_document_uses_exact_ordered_envelope() -> None:
    errors = ValidationErrorReport(
        errors=(
            ValidationError(
                "workers[1].timeout_ms",
                "out_of_range",
                "timeout_ms must be greater than 0",
            ),
        )
    )

    document = validation_error_report_to_document(errors)

    assert list(document) == ["errors"]
    assert list(document["errors"][0]) == ["field", "code", "message"]
    assert json.loads(dumps_validation_errors(errors)) == document


def test_write_report_supports_stdout_like_streams() -> None:
    stream = StringIO()
    write_aggregation_report(report(), stream)
    assert json.loads(stream.getvalue())["complete"] is True


def test_write_report_supports_utf8_file_paths(tmp_path) -> None:
    output = tmp_path / "report.json"
    write_aggregation_report(report(), output)
    assert json.loads(output.read_text(encoding="utf-8"))["total_duration_ms"] == 13.0
