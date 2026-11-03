"""Pure collect-all validation for JSON run-configuration documents."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from .models import (
    RunConfiguration,
    ValidationError,
    ValidationErrorReport,
    WorkerConfiguration,
    _normalized_worker_id,
    _validated_url,
)

_TOP_LEVEL_FIELDS = ("concurrency_limit", "workers")
_WORKER_FIELDS = ("worker_id", "url", "method", "timeout_ms")


def _error(field: str, code: str, message: str) -> ValidationError:
    return ValidationError(field=field, code=code, message=message)


def validate_run_configuration(
    document: object,
) -> tuple[RunConfiguration | None, ValidationErrorReport | None]:
    """Return either a validated configuration or all detectable validation errors."""

    if not isinstance(document, Mapping):
        return None, ValidationErrorReport(
            (_error("$", "invalid_type", "configuration must be a JSON object"),)
        )

    errors: list[ValidationError] = []
    for key in document:
        if key not in _TOP_LEVEL_FIELDS:
            errors.append(
                _error(str(key), "unknown_field", "unknown top-level field")
            )

    concurrency_limit = document.get("concurrency_limit")
    if "concurrency_limit" not in document:
        errors.append(
            _error("concurrency_limit", "required", "concurrency_limit is required")
        )
        valid_concurrency = False
    elif isinstance(concurrency_limit, bool) or not isinstance(concurrency_limit, int):
        errors.append(
            _error(
                "concurrency_limit",
                "invalid_type",
                "concurrency_limit must be an integer",
            )
        )
        valid_concurrency = False
    elif concurrency_limit < 1:
        errors.append(
            _error(
                "concurrency_limit",
                "out_of_range",
                "concurrency_limit must be greater than 0",
            )
        )
        valid_concurrency = False
    else:
        valid_concurrency = True

    workers_value = document.get("workers")
    if "workers" not in document:
        errors.append(_error("workers", "required", "workers is required"))
        workers: list[object] | None = None
    elif not isinstance(workers_value, list):
        errors.append(_error("workers", "invalid_type", "workers must be an array"))
        workers = None
    else:
        workers = workers_value
        if not 3 <= len(workers) <= 5:
            errors.append(
                _error("workers", "worker_count", "workers must contain 3 to 5 items")
            )
        if valid_concurrency and concurrency_limit > len(workers):
            errors.append(
                _error(
                    "concurrency_limit",
                    "out_of_range",
                    "concurrency_limit must not exceed worker count",
                )
            )

    parsed_workers: list[WorkerConfiguration] = []
    seen_ids: set[str] = set()
    if workers is not None:
        for index, worker_value in enumerate(workers):
            prefix = f"workers[{index}]"
            if not isinstance(worker_value, Mapping):
                errors.append(
                    _error(prefix, "invalid_type", "worker must be a JSON object")
                )
                continue

            local_errors_before = len(errors)
            for key in worker_value:
                if key not in _WORKER_FIELDS:
                    errors.append(
                        _error(
                            f"{prefix}.{key}",
                            "unknown_field",
                            "unknown worker field",
                        )
                    )
            for field in _WORKER_FIELDS:
                if field not in worker_value:
                    errors.append(
                        _error(f"{prefix}.{field}", "required", f"{field} is required")
                    )

            worker_id: str | None = None
            if "worker_id" in worker_value:
                raw_id = worker_value["worker_id"]
                try:
                    worker_id = _normalized_worker_id(raw_id)
                except TypeError:
                    errors.append(
                        _error(
                            f"{prefix}.worker_id",
                            "invalid_type",
                            "worker_id must be a string",
                        )
                    )
                except ValueError as exc:
                    code = "required" if not str(raw_id).strip() else "out_of_range"
                    errors.append(_error(f"{prefix}.worker_id", code, str(exc)))
                else:
                    if worker_id in seen_ids:
                        errors.append(
                            _error(
                                f"{prefix}.worker_id",
                                "duplicate",
                                "worker_id must be unique within the run",
                            )
                        )
                    seen_ids.add(worker_id)

            url: str | None = None
            if "url" in worker_value:
                try:
                    url = _validated_url(worker_value["url"])
                except TypeError as exc:
                    errors.append(
                        _error(f"{prefix}.url", "invalid_type", str(exc))
                    )
                except ValueError as exc:
                    errors.append(_error(f"{prefix}.url", "invalid_url", str(exc)))

            method = worker_value.get("method")
            if "method" in worker_value:
                if not isinstance(method, str):
                    errors.append(
                        _error(
                            f"{prefix}.method",
                            "invalid_type",
                            "method must be a string",
                        )
                    )
                elif method != "GET":
                    errors.append(
                        _error(
                            f"{prefix}.method",
                            "unsupported_method",
                            "only GET is supported",
                        )
                    )

            timeout_ms = worker_value.get("timeout_ms")
            if "timeout_ms" in worker_value:
                if isinstance(timeout_ms, bool) or not isinstance(timeout_ms, int):
                    errors.append(
                        _error(
                            f"{prefix}.timeout_ms",
                            "invalid_type",
                            "timeout_ms must be an integer",
                        )
                    )
                elif timeout_ms <= 0:
                    errors.append(
                        _error(
                            f"{prefix}.timeout_ms",
                            "out_of_range",
                            "timeout_ms must be greater than 0",
                        )
                    )

            if (
                len(errors) == local_errors_before
                and worker_id is not None
                and url is not None
                and method == "GET"
                and isinstance(timeout_ms, int)
                and not isinstance(timeout_ms, bool)
                and timeout_ms > 0
            ):
                parsed_workers.append(
                    WorkerConfiguration(worker_id, url, method, timeout_ms)
                )

    if errors:
        return None, ValidationErrorReport(tuple(errors))

    assert workers is not None
    assert isinstance(concurrency_limit, int) and not isinstance(concurrency_limit, bool)
    return RunConfiguration(tuple(parsed_workers), concurrency_limit), None
