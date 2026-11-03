"""Deterministic successful demonstration scenarios."""

from __future__ import annotations

from dataclasses import dataclass

from .aggregator import aggregate
from .models import (
    AggregationReport,
    RunConfiguration,
    SimulatedWorkerConfiguration,
    WorkerBehavior,
)
from .worker import WorkerFleet


@dataclass(frozen=True, slots=True)
class Scenario:
    name: str
    description: str
    workers: tuple[SimulatedWorkerConfiguration, ...]
    timeout_ms: int


NORMAL = Scenario(
    name="normal",
    description="Three successful workers with different deterministic delays.",
    workers=tuple(
        SimulatedWorkerConfiguration(
            worker_id=f"worker-{suffix}",
            behavior=WorkerBehavior.SUCCESS,
            delay_ms=delay_ms,
            data={"value": value},
        )
        for suffix, delay_ms, value in (
            ("a", 60, 10),
            ("b", 120, 20),
            ("c", 180, 30),
        )
    ),
    timeout_ms=1_000,
)

SIMULTANEOUS = Scenario(
    name="simultaneous",
    description="Five successful workers configured to finish together.",
    workers=tuple(
        SimulatedWorkerConfiguration(
            worker_id=f"worker-{index}",
            behavior=WorkerBehavior.SUCCESS,
            delay_ms=30,
            data={"value": index},
        )
        for index in range(5)
    ),
    timeout_ms=1_000,
)

SCENARIOS = (NORMAL, SIMULTANEOUS)


def get_scenario(name: str) -> Scenario:
    for scenario in SCENARIOS:
        if scenario.name == name:
            return scenario
    raise KeyError(name)


async def run_scenario(scenario: Scenario) -> AggregationReport:
    async with WorkerFleet(scenario.workers) as fleet:
        workers = fleet.worker_configurations(timeout_ms=scenario.timeout_ms)
        return await aggregate(
            RunConfiguration(workers=workers, concurrency_limit=len(workers))
        )
