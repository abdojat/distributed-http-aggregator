"""Shared deterministic fixtures and timing/task-leak helpers."""

from __future__ import annotations

import asyncio
from collections.abc import Callable

import pytest

from distributed_http_aggregator.models import RunConfiguration, WorkerConfiguration


def timing_tolerance_seconds(longest_seconds: float) -> float:
    """Return the documented Windows-safe timing tolerance in seconds."""

    if longest_seconds < 0:
        raise ValueError("longest_seconds must be non-negative")
    return max(0.15, 0.25 * longest_seconds)


def pending_tasks_snapshot() -> frozenset[asyncio.Task[object]]:
    """Capture owned pending tasks while excluding the calling test task."""

    current = asyncio.current_task()
    return frozenset(
        task
        for task in asyncio.all_tasks()
        if task is not current and not task.done()
    )


async def assert_no_new_pending_tasks(
    before: frozenset[asyncio.Task[object]],
) -> None:
    """Allow cancellation callbacks to settle, then reject newly leaked tasks."""

    await asyncio.sleep(0)
    leaked = pending_tasks_snapshot() - before
    assert not leaked, f"new pending tasks leaked: {sorted(map(repr, leaked))}"


@pytest.fixture
def timing_tolerance() -> Callable[[float], float]:
    return timing_tolerance_seconds


@pytest.fixture
def sample_worker_configurations() -> tuple[WorkerConfiguration, ...]:
    return tuple(
        WorkerConfiguration(
            worker_id=f"worker-{suffix}",
            url=f"http://127.0.0.1:{8100 + index}/work",
            method="GET",
            timeout_ms=500,
        )
        for index, suffix in enumerate(("a", "b", "c"), start=1)
    )


@pytest.fixture
def sample_run_configuration(
    sample_worker_configurations: tuple[WorkerConfiguration, ...],
) -> RunConfiguration:
    return RunConfiguration(
        workers=sample_worker_configurations,
        concurrency_limit=len(sample_worker_configurations),
    )
