import asyncio

import pytest

from conftest import assert_no_new_pending_tasks, pending_tasks_snapshot
from distributed_http_aggregator.models import RunConfiguration, WorkerConfiguration


def test_shared_sample_configuration_fixtures(
    sample_worker_configurations: tuple[WorkerConfiguration, ...],
    sample_run_configuration: RunConfiguration,
) -> None:
    assert len(sample_worker_configurations) == 3
    assert sample_run_configuration.workers == sample_worker_configurations
    assert sample_run_configuration.concurrency_limit == 3


def test_timing_tolerance_uses_floor_and_percentage(timing_tolerance) -> None:
    assert timing_tolerance(0.2) == pytest.approx(0.15)
    assert timing_tolerance(1.0) == pytest.approx(0.25)


async def test_pending_task_snapshot_ignores_current_task() -> None:
    before = pending_tasks_snapshot()
    await asyncio.sleep(0)
    await assert_no_new_pending_tasks(before)
