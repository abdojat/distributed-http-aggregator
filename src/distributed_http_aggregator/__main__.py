"""Package entry point; command dispatch is introduced with the CLI tasks."""
"""Module entry point for ``python -m distributed_http_aggregator``."""

from .cli import main


if __name__ == "__main__":
    raise SystemExit(main())
