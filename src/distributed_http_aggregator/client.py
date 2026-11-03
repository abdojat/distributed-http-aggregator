"""One non-blocking HTTP operation for a configured worker."""

from __future__ import annotations

import asyncio
import ssl
import time
from contextlib import asynccontextmanager
from collections.abc import AsyncIterator
from typing import Any

import httpx

from .models import WorkerConfiguration, WorkerOutcome, WorkerStatus


_SSL_CONTEXT = ssl.create_default_context()


class InvalidWorkerResponse(ValueError):
    """The remote response does not satisfy the successful worker contract."""


def create_async_client(concurrency_limit: int) -> httpx.AsyncClient:
    """Create a bounded client while reusing the process TLS trust context."""

    limits = httpx.Limits(
        max_connections=concurrency_limit,
        max_keepalive_connections=concurrency_limit,
    )
    return httpx.AsyncClient(
        limits=limits,
        verify=_SSL_CONTEXT,
        trust_env=False,
    )


@asynccontextmanager
async def _request_permit(
    semaphore: asyncio.Semaphore | None,
) -> AsyncIterator[None]:
    if semaphore is None:
        yield
        return
    async with semaphore:
        yield


def _validated_payload(response: httpx.Response, worker_id: str) -> Any:
    payload = response.json()
    if not isinstance(payload, dict):
        raise InvalidWorkerResponse("response JSON must be an object")
    if "worker_id" not in payload or "data" not in payload:
        raise InvalidWorkerResponse("response must contain worker_id and data")
    if payload["worker_id"] != worker_id:
        raise InvalidWorkerResponse("response worker_id does not match configuration")
    return payload["data"]


async def request_worker(
    client: httpx.AsyncClient,
    configuration: WorkerConfiguration,
    semaphore: asyncio.Semaphore | None = None,
) -> WorkerOutcome:
    """Request one successful worker under an independent whole-operation deadline."""

    started_ns = time.perf_counter_ns()
    timeout_seconds = configuration.timeout_ms / 1_000
    async with asyncio.timeout(timeout_seconds):
        async with _request_permit(semaphore):
            response = await client.request(
                configuration.method,
                configuration.url,
                headers={"Accept": "application/json"},
                timeout=httpx.Timeout(timeout_seconds),
            )
            response.raise_for_status()
            data = _validated_payload(response, configuration.worker_id)

    duration_ms = (time.perf_counter_ns() - started_ns) / 1_000_000
    return WorkerOutcome(
        worker_id=configuration.worker_id,
        status=WorkerStatus.SUCCESS,
        duration_ms=duration_ms,
        data=data,
        http_status=response.status_code,
        failure_code=None,
        failure_reason=None,
    )
