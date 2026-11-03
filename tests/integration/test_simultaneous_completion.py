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
async def test_twenty_simultaneous_completion_runs_are_exact_and_state_isolated() -> None:
    simulated = tuple(
        SimulatedWorkerConfiguration(
            worker_id=f"worker-{index}",
            behavior=WorkerBehavior.SUCCESS,
            delay_ms=15,
            data={"sequence": index},
        )
        for index in range(5)
    )
    expected_ids = tuple(worker.worker_id for worker in simulated)
    expected_data = tuple(worker.data for worker in simulated)

    async with WorkerFleet(simulated) as fleet:
        configuration = RunConfiguration(
            workers=fleet.worker_configurations(timeout_ms=500),
            concurrency_limit=5,
        )
        for run_number in range(20):
            report = await aggregate(configuration)
            assert len(report.worker_outcomes) == 5, f"run {run_number}"
            assert tuple(item.worker_id for item in report.worker_outcomes) == expected_ids
            assert tuple(item.status for item in report.worker_outcomes) == (
                WorkerStatus.SUCCESS,
            ) * 5
            assert tuple(item.data for item in report.worker_outcomes) == expected_data
            assert tuple(item.worker_id for item in report.aggregated_data) == expected_ids
            assert len(report.aggregated_data) == 5

    print("RELIABILITY runs=20 passed=20")
