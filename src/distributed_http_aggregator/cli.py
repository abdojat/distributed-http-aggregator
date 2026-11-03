"""PowerShell-compatible command-line interface."""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from collections.abc import Sequence
from pathlib import Path
from typing import TextIO

from .models import SimulatedWorkerConfiguration, WorkerBehavior
from .scenarios import SCENARIOS, get_scenario, run_scenario
from .serialization import write_aggregation_report
from .worker import WorkerFleet


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="distributed_http_aggregator")
    commands = parser.add_subparsers(dest="command", required=True)

    commands.add_parser("list-scenarios", help="list deterministic scenarios")

    demo = commands.add_parser("demo", help="run a local demonstration scenario")
    demo.add_argument("--scenario", required=True, choices=[item.name for item in SCENARIOS])
    demo.add_argument("--output", type=Path)

    worker = commands.add_parser("worker", help="run one standalone local worker")
    worker.add_argument("--id", required=True)
    worker.add_argument("--host", default="127.0.0.1", choices=["127.0.0.1"])
    worker.add_argument("--port", type=int, required=True)
    worker.add_argument("--delay-ms", type=int, required=True)
    worker.add_argument(
        "--behavior", choices=["success", "http-500", "malformed"], required=True
    )
    worker.add_argument("--data-json", default="null")
    return parser


def _write_json(document: object, output: TextIO) -> None:
    json.dump(document, output, ensure_ascii=False, allow_nan=False)
    output.write("\n")


async def run_worker_command(
    arguments: argparse.Namespace,
    *,
    stop_event: asyncio.Event | None = None,
    output: TextIO | None = None,
) -> int:
    """Start a standalone worker, publish readiness, and wait for shutdown."""

    destination = output if output is not None else sys.stdout
    behavior = WorkerBehavior(arguments.behavior.replace("-", "_"))
    data = json.loads(arguments.data_json)
    configuration = SimulatedWorkerConfiguration(
        worker_id=arguments.id,
        behavior=behavior,
        delay_ms=arguments.delay_ms,
        data=data,
        listen_host=arguments.host,
        listen_port=arguments.port,
    )
    async with WorkerFleet((configuration,)) as fleet:
        running = fleet.workers[0]
        _write_json(
            {
                "worker_id": configuration.worker_id,
                "status": "ready",
                "url": running.base_url,
            },
            destination,
        )
        destination.flush()
        shutdown = stop_event if stop_event is not None else asyncio.Event()
        await shutdown.wait()
    return 0


async def _dispatch(arguments: argparse.Namespace) -> int:
    if arguments.command == "demo":
        report = await run_scenario(get_scenario(arguments.scenario))
        write_aggregation_report(report, arguments.output)
        return 0
    if arguments.command == "worker":
        return await run_worker_command(arguments)
    raise RuntimeError(f"unexpected asynchronous command: {arguments.command}")


def main(argv: Sequence[str] | None = None) -> int:
    arguments = build_parser().parse_args(argv)
    if arguments.command == "list-scenarios":
        _write_json(
            [
                {"name": scenario.name, "description": scenario.description}
                for scenario in SCENARIOS
            ],
            sys.stdout,
        )
        return 0
    try:
        return asyncio.run(_dispatch(arguments))
    except KeyboardInterrupt:
        return 130
