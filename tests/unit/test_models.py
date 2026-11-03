import asyncio
from dataclasses import FrozenInstanceError

import pytest

from distributed_http_aggregator.models import (
    AggregatedDataItem,
    AggregationCancelled,
    AggregationReport,
    FailureCode,
    RunConfiguration,
    SimulatedWorkerConfiguration,
    ValidationError,
    ValidationErrorReport,
    WorkerBehavior,
    WorkerConfiguration,
    WorkerOutcome,
    WorkerStatus,
)


def worker(worker_id: str = "worker-a") -> WorkerConfiguration:
    return WorkerConfiguration(
        worker_id=worker_id,
        url=f"http://127.0.0.1/{worker_id}",
        method="GET",
        timeout_ms=500,
    )


def success(worker_id: str = "worker-a", data: object = None) -> WorkerOutcome:
    return WorkerOutcome(
        worker_id=worker_id,
        status=WorkerStatus.SUCCESS,
        duration_ms=12.5,
        data=data,
        http_status=200,
        failure_code=None,
        failure_reason=None,
    )


def failure(worker_id: str = "worker-b") -> WorkerOutcome:
    return WorkerOutcome(
        worker_id=worker_id,
        status=WorkerStatus.TIMEOUT,
        duration_ms=500.0,
        data=None,
        http_status=None,
        failure_code=FailureCode.REQUEST_TIMEOUT,
        failure_reason="request timed out",
    )


def test_enums_use_published_string_values() -> None:
    assert WorkerBehavior.SUCCESS.value == "success"
    assert WorkerBehavior.HTTP_500.value == "http_500"
    assert WorkerStatus.CONNECTION_FAILURE.value == "connection_failure"
    assert WorkerStatus.CANCELLED.value == "cancelled"
    assert FailureCode.WORKER_IDENTITY_MISMATCH.value == "worker_identity_mismatch"


def test_worker_configuration_is_frozen_and_normalizes_identity() -> None:
    config = WorkerConfiguration(
        worker_id="  worker-a  ",
        url="http://localhost:8080/work",
        method="GET",
        timeout_ms=100,
    )

    assert config.worker_id == "worker-a"
    with pytest.raises(FrozenInstanceError):
        config.timeout_ms = 200  # type: ignore[misc]


@pytest.mark.parametrize(
    ("overrides", "message"),
    [
        ({"worker_id": "   "}, "worker_id"),
        ({"worker_id": "x" * 65}, "64"),
        ({"url": "ftp://localhost/work"}, "http"),
        ({"url": "http://user:secret@localhost/work"}, "credentials"),
        ({"url": "http:///work"}, "host"),
        ({"method": "POST"}, "GET"),
        ({"timeout_ms": 0}, "greater than 0"),
        ({"timeout_ms": True}, "integer"),
    ],
)
def test_worker_configuration_rejects_invalid_local_values(
    overrides: dict[str, object], message: str
) -> None:
    values: dict[str, object] = {
        "worker_id": "worker-a",
        "url": "http://localhost/work",
        "method": "GET",
        "timeout_ms": 100,
    }
    values.update(overrides)

    with pytest.raises((TypeError, ValueError), match=message):
        WorkerConfiguration(**values)  # type: ignore[arg-type]


def test_isolated_worker_configuration_does_not_enforce_cross_worker_uniqueness() -> None:
    first = worker("same-id")
    second = worker("same-id")
    assert first.worker_id == second.worker_id


def test_simulated_worker_configuration_validates_local_server_values() -> None:
    config = SimulatedWorkerConfiguration(
        worker_id=" worker-a ",
        behavior=WorkerBehavior.SUCCESS,
        delay_ms=0,
        data={"value": 1},
        listen_host="127.0.0.1",
        listen_port=0,
    )
    assert config.worker_id == "worker-a"

    with pytest.raises(ValueError, match="delay_ms"):
        SimulatedWorkerConfiguration("worker-a", WorkerBehavior.SUCCESS, -1)
    with pytest.raises(ValueError, match="listen_host"):
        SimulatedWorkerConfiguration(
            "worker-a", WorkerBehavior.SUCCESS, 0, listen_host="0.0.0.0"
        )
    with pytest.raises(ValueError, match="listen_port"):
        SimulatedWorkerConfiguration(
            "worker-a", WorkerBehavior.SUCCESS, 0, listen_port=65_536
        )


def test_run_configuration_preserves_order_without_owning_local_validation() -> None:
    configs = [worker("worker-a"), worker("worker-b"), worker("worker-c")]
    run = RunConfiguration(workers=configs, concurrency_limit=3)

    assert run.workers == tuple(configs)
    with pytest.raises(FrozenInstanceError):
        run.concurrency_limit = 2  # type: ignore[misc]


def test_validation_error_report_is_non_empty_and_immutable() -> None:
    error = ValidationError("workers", "worker_count", "requires 3 to 5 workers")
    report = ValidationErrorReport(errors=[error])

    assert report.errors == (error,)
    with pytest.raises(ValueError, match="non-empty"):
        ValidationErrorReport(errors=[])


def test_success_outcome_requires_2xx_status_and_null_failure_fields() -> None:
    outcome = success(data=None)
    assert outcome.data is None
    assert outcome.http_status == 200

    with pytest.raises(ValueError, match="2xx"):
        WorkerOutcome(
            worker_id="worker-a",
            status=WorkerStatus.SUCCESS,
            duration_ms=1,
            data={},
            http_status=500,
            failure_code=None,
            failure_reason=None,
        )
    with pytest.raises(ValueError, match="failure"):
        WorkerOutcome(
            worker_id="worker-a",
            status=WorkerStatus.SUCCESS,
            duration_ms=1,
            data={},
            http_status=200,
            failure_code=FailureCode.HTTP_STATUS,
            failure_reason="not allowed",
        )


def test_failure_outcome_requires_null_data_and_failure_details() -> None:
    outcome = failure()
    assert outcome.data is None

    with pytest.raises(ValueError, match="data"):
        WorkerOutcome(
            worker_id="worker-b",
            status=WorkerStatus.TIMEOUT,
            duration_ms=1,
            data={"unexpected": True},
            http_status=None,
            failure_code=FailureCode.REQUEST_TIMEOUT,
            failure_reason="timed out",
        )
    with pytest.raises(ValueError, match="failure_code"):
        WorkerOutcome(
            worker_id="worker-b",
            status=WorkerStatus.TIMEOUT,
            duration_ms=1,
            data=None,
            http_status=None,
            failure_code=None,
            failure_reason="timed out",
        )


@pytest.mark.parametrize("duration", [-1, float("inf"), float("nan")])
def test_outcomes_reject_invalid_durations(duration: float) -> None:
    with pytest.raises(ValueError, match="duration_ms"):
        WorkerOutcome(
            worker_id="worker-a",
            status=WorkerStatus.SUCCESS,
            duration_ms=duration,
            data=None,
            http_status=200,
            failure_code=None,
            failure_reason=None,
        )


def test_aggregation_report_enforces_order_and_success_projection() -> None:
    outcomes = (success("worker-a", {"value": 1}), failure("worker-b"))
    report = AggregationReport(
        complete=True,
        total_duration_ms=501,
        worker_outcomes=outcomes,
        aggregated_data=(AggregatedDataItem("worker-a", {"value": 1}),),
    )

    assert report.worker_outcomes == outcomes
    assert report.aggregated_data[0].worker_id == "worker-a"

    with pytest.raises(ValueError, match="configured order"):
        AggregationReport(
            complete=True,
            total_duration_ms=501,
            worker_outcomes=outcomes,
            aggregated_data=(AggregatedDataItem("worker-b", {"value": 1}),),
        )


def test_all_failure_report_accepts_empty_aggregated_data() -> None:
    report = AggregationReport(
        complete=True,
        total_duration_ms=500,
        worker_outcomes=(failure("worker-a"), failure("worker-b")),
        aggregated_data=(),
    )
    assert report.aggregated_data == ()


def test_report_rejects_duplicate_worker_outcomes() -> None:
    with pytest.raises(ValueError, match="exactly once"):
        AggregationReport(
            complete=True,
            total_duration_ms=10,
            worker_outcomes=(success("worker-a"), success("worker-a")),
            aggregated_data=(
                AggregatedDataItem("worker-a", None),
                AggregatedDataItem("worker-a", None),
            ),
        )


def test_aggregation_cancelled_remains_an_asyncio_cancellation() -> None:
    report = AggregationReport(
        complete=False,
        total_duration_ms=10,
        worker_outcomes=(
            WorkerOutcome(
                worker_id="worker-a",
                status=WorkerStatus.CANCELLED,
                duration_ms=10,
                data=None,
                http_status=None,
                failure_code=FailureCode.CANCELLED,
                failure_reason="aggregation cancelled",
            ),
        ),
        aggregated_data=(),
    )
    error = AggregationCancelled(report)

    assert isinstance(error, asyncio.CancelledError)
    assert error.partial_report is report
