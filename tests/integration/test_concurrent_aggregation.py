from __future__ import annotations

import pytest

from distributed_http_aggregator.aggregator import aggregate
from distributed_http_aggregator.models import (
    RunConfiguration,
    SimulatedWorkerConfiguration,
    WorkerBehavior,
    WorkerStatus,
)
from distributed_http_aggregator.worker import WorkerFleet


@pytest.mark.asyncio
@pytest.mark.parametrize("worker_count", [3, 5])
async def test_real_workers_are_aggregated_concurrently_in_configured_order(
    worker_count: int, timing_tolerance
) -> None:
    delays_ms = tuple(100 * (index + 1) for index in range(worker_count))
    simulated = tuple(
        SimulatedWorkerConfiguration(
            worker_id=f"worker-{index}",
            behavior=WorkerBehavior.SUCCESS,
            delay_ms=delay_ms,
            data={"source": index},
        )
        for index, delay_ms in enumerate(delays_ms)
    )

    async with WorkerFleet(simulated) as fleet:
        configuration = RunConfiguration(
            workers=fleet.worker_configurations(timeout_ms=1_000),
            concurrency_limit=worker_count,
        )
        report = await aggregate(configuration)

    expected_ids = tuple(item.worker_id for item in simulated)
    assert report.complete
    assert tuple(outcome.worker_id for outcome in report.worker_outcomes) == expected_ids
    assert all(
        outcome.status is WorkerStatus.SUCCESS for outcome in report.worker_outcomes
    )
    assert tuple(outcome.data for outcome in report.worker_outcomes) == tuple(
        item.data for item in simulated
    )
    assert tuple(item.worker_id for item in report.aggregated_data) == expected_ids
    assert report.total_duration_ms >= max(delays_ms) * 0.8
    assert report.total_duration_ms / 1_000 <= (
        max(delays_ms) / 1_000 + timing_tolerance(max(delays_ms) / 1_000)
    )
    assert report.total_duration_ms < sum(delays_ms)
