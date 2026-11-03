"""Structured concurrent orchestration and deterministic report assembly."""

from __future__ import annotations

import asyncio
import time

from .client import create_async_client, request_worker
from .models import (
    AggregatedDataItem,
    AggregationReport,
    RunConfiguration,
    WorkerOutcome,
)


async def aggregate(configuration: RunConfiguration) -> AggregationReport:
    """Request every worker concurrently and assemble results in configured order."""

    started_ns = time.perf_counter_ns()
    semaphore = asyncio.Semaphore(configuration.concurrency_limit)
    tasks: list[asyncio.Task[WorkerOutcome]] = []
    async with create_async_client(configuration.concurrency_limit) as client:
        async with asyncio.TaskGroup() as task_group:
            for worker in configuration.workers:
                tasks.append(
                    task_group.create_task(request_worker(client, worker, semaphore))
                )

    outcomes = tuple(task.result() for task in tasks)
    aggregated_data = tuple(
        AggregatedDataItem(worker_id=outcome.worker_id, data=outcome.data)
        for outcome in outcomes
    )
    total_duration_ms = (time.perf_counter_ns() - started_ns) / 1_000_000
    return AggregationReport(
        complete=True,
        total_duration_ms=total_duration_ms,
        worker_outcomes=outcomes,
        aggregated_data=aggregated_data,
    )
