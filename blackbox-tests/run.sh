#!/usr/bin/env bash
# Runs the black-box suite against the packaged CLI jar (specs/012-blackbox-cli-tests/).
# Needs: `mvn package` already run, PostgreSQL up, Python 3.11+.
# Usage: blackbox-tests/run.sh [--include-pending] [extra behave arguments]
set -euo pipefail
cd "$(dirname "$0")"

tags=(--tags "not @pending")
if [[ "${1:-}" == "--include-pending" ]]; then
  tags=()
  shift
fi

if [[ ! -x .venv/bin/python ]]; then
  python3 -m venv .venv
fi
.venv/bin/python -m pip install --quiet --disable-pip-version-check -r requirements.txt

.venv/bin/python -m pytest --quiet tests
.venv/bin/python -m behave "${tags[@]}" "$@"
