#!/usr/bin/env python3
"""Fail when a GitHub workflow references a remote action without a full commit SHA."""

from __future__ import annotations

import re
import sys
from pathlib import Path

WORKFLOW_DIRECTORY = Path(".github/workflows")
USES_PATTERN = re.compile(r"^\s*uses:\s*([^\s#]+)\s*(?:#.*)?$")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def main() -> int:
    failures: list[str] = []
    for workflow in sorted(WORKFLOW_DIRECTORY.glob("*.y*ml")):
        for line_number, line in enumerate(workflow.read_text().splitlines(), start=1):
            match = USES_PATTERN.match(line)
            if match is None:
                continue
            reference = match.group(1)
            if reference.startswith(("./", "docker://")):
                continue
            action, separator, revision = reference.rpartition("@")
            if not separator or not action or not FULL_SHA_PATTERN.fullmatch(revision):
                failures.append(f"{workflow}:{line_number}: unpinned action: {reference}")

    if failures:
        print("GitHub Actions must use full 40-character commit SHAs.", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1

    print("All remote GitHub Actions are pinned to commit SHAs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
