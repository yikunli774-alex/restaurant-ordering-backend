#!/usr/bin/env bash
# Quick check that the running stack is actually alive: readiness + API docs.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl --fail --silent --show-error \
  "${BASE_URL}/actuator/health/readiness" \
  | grep --quiet '"status":"UP"'

curl --fail --silent --show-error \
  "${BASE_URL}/v3/api-docs" \
  | grep --quiet '"title":"Restaurant Ordering API"'

echo "Smoke test passed: ${BASE_URL}"
