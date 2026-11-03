from __future__ import annotations

import asyncio

import httpx
import pytest

from distributed_http_aggregator.client import request_worker
from distributed_http_aggregator.models import WorkerConfiguration, WorkerStatus


@pytest.mark.asyncio
@pytest.mark.parametrize("data", [{"value": [1, 2]}, None, "text", 0, False])
async def test_successful_response_maps_identity_data_and_duration(data: object) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        await asyncio.sleep(0.01)
        return httpx.Response(
            200,
            json={"worker_id": "worker-a", "data": data},
            request=request,
        )

    configuration = WorkerConfiguration(
        worker_id="worker-a",
        url="http://worker.test/work",
        timeout_ms=250,
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        outcome = await request_worker(client, configuration)

    assert outcome.worker_id == "worker-a"
    assert outcome.status is WorkerStatus.SUCCESS
    assert outcome.data == data
    assert outcome.http_status == 200
    assert outcome.failure_code is None
    assert outcome.failure_reason is None
    assert outcome.duration_ms >= 5


@pytest.mark.asyncio
async def test_client_accepts_any_successful_2xx_status() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            206,
            json={"worker_id": "worker-a", "data": ["partial"]},
            request=request,
        )

    configuration = WorkerConfiguration("worker-a", "http://worker.test/work")
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        outcome = await request_worker(client, configuration)

    assert outcome.http_status == 206
    assert outcome.data == ["partial"]
