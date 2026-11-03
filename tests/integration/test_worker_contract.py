from __future__ import annotations

import time

import httpx
import pytest

from distributed_http_aggregator.models import (
    SimulatedWorkerConfiguration,
    WorkerBehavior,
)
from distributed_http_aggregator.worker import WorkerFleet


@pytest.mark.asyncio
async def test_success_worker_contract_delay_and_os_assigned_port() -> None:
    configuration = SimulatedWorkerConfiguration(
        worker_id="worker-a",
        behavior=WorkerBehavior.SUCCESS,
        delay_ms=50,
        data={"value": 7},
    )

    async with WorkerFleet((configuration,)) as fleet:
        worker = fleet.workers[0]
        assert worker.port > 0
        assert worker.host == "127.0.0.1"

        async with httpx.AsyncClient() as client:
            health = await client.get(f"{worker.base_url}/health")
            started_ns = time.perf_counter_ns()
            response = await client.get(f"{worker.base_url}/work")
            elapsed_ms = (time.perf_counter_ns() - started_ns) / 1_000_000

        assert health.status_code == 200
        assert health.json() == {"worker_id": "worker-a", "status": "ready"}
        assert response.status_code == 200
        assert response.json() == {"worker_id": "worker-a", "data": {"value": 7}}
        assert elapsed_ms >= 40

    assert worker.closed


@pytest.mark.asyncio
@pytest.mark.parametrize("data", [None, "", [], {}])
async def test_success_worker_preserves_empty_and_null_data(data: object) -> None:
    configuration = SimulatedWorkerConfiguration(
        worker_id="worker-empty",
        behavior=WorkerBehavior.SUCCESS,
        delay_ms=0,
        data=data,
    )

    async with WorkerFleet((configuration,)) as fleet:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{fleet.workers[0].base_url}/work")

    assert response.json() == {"worker_id": "worker-empty", "data": data}


@pytest.mark.asyncio
async def test_worker_fleet_cleans_up_in_reverse_start_order() -> None:
    configurations = tuple(
        SimulatedWorkerConfiguration(
            worker_id=f"worker-{index}",
            behavior=WorkerBehavior.SUCCESS,
            delay_ms=0,
            data=index,
        )
        for index in range(3)
    )
    fleet = WorkerFleet(configurations)

    await fleet.start()
    workers = fleet.workers
    await fleet.close()

    assert all(worker.closed for worker in workers)
    assert fleet.cleanup_order == tuple(
        worker.configuration.worker_id for worker in reversed(workers)
    )
