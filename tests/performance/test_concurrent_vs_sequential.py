from __future__ import annotations

import asyncio
import time

import pytest

from distributed_http_aggregator.aggregator import aggregate
from distributed_http_aggregator.client import create_async_client, request_worker
from distributed_http_aggregator.models import (
    RunConfiguration,
    SimulatedWorkerConfiguration,
    WorkerBehavior,
)
from distributed_http_aggregator.worker import WorkerFleet


@pytest.mark.asyncio
async def test_concurrent_execution_beats_controlled_sequential_baseline(
    timing_tolerance,
) -> None:
    delays = (0.15, 0.30, 0.45)
    simulated = tuple(
        SimulatedWorkerConfiguration(
            worker_id=f"worker-{index}",
            behavior=WorkerBehavior.SUCCESS,
            delay_ms=round(delay * 1_000),
            data=index,
        )
        for index, delay in enumerate(delays)
    )

    async with WorkerFleet(simulated) as fleet:
        workers = fleet.worker_configurations(timeout_ms=2_000)
        concurrent_started = time.perf_counter_ns()
        await aggregate(
            RunConfiguration(workers=workers, concurrency_limit=len(workers))
        )
        concurrent_seconds = (time.perf_counter_ns() - concurrent_started) / 1e9

        sequential_started = time.perf_counter_ns()
        async with create_async_client(1) as client:
            semaphore = asyncio.Semaphore(1)
            for worker in workers:
                await request_worker(client, worker, semaphore)
        sequential_seconds = (time.perf_counter_ns() - sequential_started) / 1e9

    configured_sum = sum(delays)
    longest = max(delays)
    print(
        "PERFORMANCE "
        f"concurrent={concurrent_seconds:.6f}s "
        f"sequential={sequential_seconds:.6f}s "
        f"configured_sum={configured_sum:.6f}s longest={longest:.6f}s"
    )
    assert concurrent_seconds <= longest + timing_tolerance(longest)
    assert concurrent_seconds < 0.75 * configured_sum
    assert concurrent_seconds < 0.75 * sequential_seconds
