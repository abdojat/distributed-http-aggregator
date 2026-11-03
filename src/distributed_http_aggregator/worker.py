"""Lifecycle-managed local HTTP workers for deterministic simulations."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field

from aiohttp import web

from .models import (
    SimulatedWorkerConfiguration,
    WorkerBehavior,
    WorkerConfiguration,
)


def create_worker_application(
    configuration: SimulatedWorkerConfiguration,
) -> web.Application:
    """Create one isolated worker application with fixed behavior."""

    if configuration.behavior is not WorkerBehavior.SUCCESS:
        raise ValueError("only successful worker behavior is available in User Story 1")

    async def work(_: web.Request) -> web.Response:
        await asyncio.sleep(configuration.delay_ms / 1_000)
        return web.json_response(
            {"worker_id": configuration.worker_id, "data": configuration.data}
        )

    async def health(_: web.Request) -> web.Response:
        return web.json_response(
            {"worker_id": configuration.worker_id, "status": "ready"}
        )

    application = web.Application()
    application.router.add_get("/work", work)
    application.router.add_get("/health", health)
    return application


@dataclass(slots=True)
class RunningWorker:
    """A started aiohttp runner/site and its discovered loopback address."""

    configuration: SimulatedWorkerConfiguration
    runner: web.AppRunner
    site: web.TCPSite
    host: str
    port: int
    closed: bool = field(default=False, init=False)

    @property
    def base_url(self) -> str:
        return f"http://{self.host}:{self.port}"

    async def close(self) -> None:
        if self.closed:
            return
        await self.runner.cleanup()
        self.closed = True


async def start_worker(
    configuration: SimulatedWorkerConfiguration,
) -> RunningWorker:
    """Start one worker and discover an OS-assigned port when requested."""

    application = create_worker_application(configuration)
    runner = web.AppRunner(application)
    await runner.setup()
    site = web.TCPSite(
        runner,
        host=configuration.listen_host,
        port=configuration.listen_port,
    )
    try:
        await site.start()
        addresses = runner.addresses
        if not addresses:
            raise RuntimeError("worker site started without a listening address")
        host, port = addresses[0][:2]
        return RunningWorker(
            configuration=configuration,
            runner=runner,
            site=site,
            host=str(host),
            port=int(port),
        )
    except BaseException:
        await runner.cleanup()
        raise


class WorkerFleet:
    """Start workers in configured order and always close them in reverse order."""

    def __init__(
        self, configurations: tuple[SimulatedWorkerConfiguration, ...]
    ) -> None:
        self._configurations = tuple(configurations)
        self._workers: list[RunningWorker] = []
        self._cleanup_order: list[str] = []

    @property
    def workers(self) -> tuple[RunningWorker, ...]:
        return tuple(self._workers)

    @property
    def cleanup_order(self) -> tuple[str, ...]:
        return tuple(self._cleanup_order)

    async def start(self) -> "WorkerFleet":
        if self._workers:
            raise RuntimeError("worker fleet is already started")
        try:
            for configuration in self._configurations:
                self._workers.append(await start_worker(configuration))
        except BaseException:
            await self.close()
            raise
        return self

    async def close(self) -> None:
        for worker in reversed(self._workers):
            if not worker.closed:
                await worker.close()
                self._cleanup_order.append(worker.configuration.worker_id)

    def worker_configurations(
        self, *, timeout_ms: int
    ) -> tuple[WorkerConfiguration, ...]:
        if len(self._workers) != len(self._configurations):
            raise RuntimeError("worker fleet must be started before creating endpoints")
        return tuple(
            WorkerConfiguration(
                worker_id=worker.configuration.worker_id,
                url=f"{worker.base_url}/work",
                timeout_ms=timeout_ms,
            )
            for worker in self._workers
        )

    async def __aenter__(self) -> "WorkerFleet":
        return await self.start()

    async def __aexit__(self, *_: object) -> None:
        await self.close()
