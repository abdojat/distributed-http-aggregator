from __future__ import annotations

import argparse
import asyncio
import io
import json

import pytest

from distributed_http_aggregator.cli import main, run_worker_command


def test_list_scenarios_writes_json_only_to_stdout(capsys) -> None:
    exit_code = main(["list-scenarios"])
    captured = capsys.readouterr()

    document = json.loads(captured.out)
    assert exit_code == 0
    assert [item["name"] for item in document] == ["normal", "simultaneous"]
    assert captured.err == ""


def test_successful_demo_writes_report_to_stdout(capsys) -> None:
    exit_code = main(["demo", "--scenario", "normal"])
    captured = capsys.readouterr()

    document = json.loads(captured.out)
    assert exit_code == 0
    assert document["complete"] is True
    assert [item["worker_id"] for item in document["worker_outcomes"]] == [
        "worker-a",
        "worker-b",
        "worker-c",
    ]
    assert captured.err == ""


def test_successful_demo_can_write_json_file(tmp_path, capsys) -> None:
    output = tmp_path / "report.json"
    exit_code = main(["demo", "--scenario", "simultaneous", "--output", str(output)])
    captured = capsys.readouterr()

    assert exit_code == 0
    assert captured.out == ""
    assert captured.err == ""
    assert json.loads(output.read_text(encoding="utf-8"))["complete"] is True


@pytest.mark.asyncio
async def test_standalone_worker_emits_readiness_and_cleans_up() -> None:
    arguments = argparse.Namespace(
        id="standalone-a",
        host="127.0.0.1",
        port=0,
        delay_ms=0,
        behavior="success",
        data_json='{"ready": true}',
    )
    stop_event = asyncio.Event()
    stop_event.set()
    output = io.StringIO()

    exit_code = await run_worker_command(
        arguments, stop_event=stop_event, output=output
    )
    readiness = json.loads(output.getvalue())

    assert exit_code == 0
    assert readiness["worker_id"] == "standalone-a"
    assert readiness["status"] == "ready"
    assert readiness["url"].startswith("http://127.0.0.1:")
