import pytest

from distributed_http_aggregator.models import RunConfiguration
from distributed_http_aggregator.validation import validate_run_configuration


def valid_document() -> dict[str, object]:
    return {
        "concurrency_limit": 3,
        "workers": [
            {
                "worker_id": f"worker-{suffix}",
                "url": f"http://127.0.0.1:810{index}/work",
                "method": "GET",
                "timeout_ms": 500,
            }
            for index, suffix in enumerate(("a", "b", "c"), start=1)
        ],
    }


def assert_error(document: object, field: str, code: str) -> None:
    config, report = validate_run_configuration(document)
    assert config is None
    assert report is not None
    assert (field, code) in [(error.field, error.code) for error in report.errors]


def test_valid_configuration_returns_immutable_run_configuration() -> None:
    config, report = validate_run_configuration(valid_document())

    assert report is None
    assert isinstance(config, RunConfiguration)
    assert config.concurrency_limit == 3
    assert [worker.worker_id for worker in config.workers] == [
        "worker-a",
        "worker-b",
        "worker-c",
    ]


@pytest.mark.parametrize("count", [0, 1, 2, 6])
def test_worker_count_must_be_between_three_and_five(count: int) -> None:
    document = valid_document()
    document["workers"] = document["workers"][:count] if count < 3 else [
        {
            "worker_id": f"worker-{index}",
            "url": f"http://127.0.0.1:{8200 + index}/work",
            "method": "GET",
            "timeout_ms": 500,
        }
        for index in range(count)
    ]
    assert_error(document, "workers", "worker_count")


def test_identity_uniqueness_is_enforced_at_run_level_after_trimming() -> None:
    document = valid_document()
    workers = document["workers"]
    assert isinstance(workers, list)
    workers[1]["worker_id"] = " worker-a "

    assert_error(document, "workers[1].worker_id", "duplicate")


@pytest.mark.parametrize(
    ("field", "value", "code"),
    [
        ("worker_id", "", "required"),
        ("worker_id", "x" * 65, "out_of_range"),
        ("url", "ftp://localhost/work", "invalid_url"),
        ("url", "http://user:secret@localhost/work", "invalid_url"),
        ("url", "http:///work", "invalid_url"),
        ("method", "POST", "unsupported_method"),
        ("timeout_ms", 0, "out_of_range"),
        ("timeout_ms", True, "invalid_type"),
    ],
)
def test_invalid_worker_fields_are_reported(
    field: str, value: object, code: str
) -> None:
    document = valid_document()
    workers = document["workers"]
    assert isinstance(workers, list)
    workers[0][field] = value

    assert_error(document, f"workers[0].{field}", code)


@pytest.mark.parametrize("limit", [0, 4, True])
def test_invalid_concurrency_limits_are_reported(limit: object) -> None:
    document = valid_document()
    document["concurrency_limit"] = limit
    expected = "invalid_type" if limit is True else "out_of_range"
    assert_error(document, "concurrency_limit", expected)


def test_missing_and_unknown_fields_are_rejected() -> None:
    document = valid_document()
    document["unexpected"] = True
    workers = document["workers"]
    assert isinstance(workers, list)
    del workers[0]["method"]
    workers[0]["extra"] = "not allowed"

    config, report = validate_run_configuration(document)

    assert config is None
    assert report is not None
    assert [(error.field, error.code) for error in report.errors] == [
        ("unexpected", "unknown_field"),
        ("workers[0].extra", "unknown_field"),
        ("workers[0].method", "required"),
    ]


def test_validation_collects_all_detectable_errors_in_stable_order() -> None:
    document = {
        "unknown": 1,
        "concurrency_limit": 0,
        "workers": [
            {
                "worker_id": "duplicate",
                "url": "not-a-url",
                "method": "POST",
                "timeout_ms": 0,
            },
            {
                "worker_id": " duplicate ",
                "url": "http://localhost/work",
                "method": "GET",
                "timeout_ms": 100,
            },
        ],
    }

    config, report = validate_run_configuration(document)

    assert config is None
    assert report is not None
    assert [(error.field, error.code) for error in report.errors] == [
        ("unknown", "unknown_field"),
        ("concurrency_limit", "out_of_range"),
        ("workers", "worker_count"),
        ("workers[0].url", "invalid_url"),
        ("workers[0].method", "unsupported_method"),
        ("workers[0].timeout_ms", "out_of_range"),
        ("workers[1].worker_id", "duplicate"),
    ]


@pytest.mark.parametrize("document", [None, [], "invalid"])
def test_top_level_document_must_be_an_object(document: object) -> None:
    assert_error(document, "$", "invalid_type")
