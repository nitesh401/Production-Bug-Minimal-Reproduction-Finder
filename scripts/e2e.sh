#!/usr/bin/env bash
# Runs the full local automated test suite: pure-core unit tests, Spring slice tests, and
# Testcontainers-backed integration tests (needs Docker). Run from the repo root.
set -euo pipefail
echo "== Unit + slice tests (fast, no Docker needed except for *IT classes) =="
mvn -q -B clean test

echo
echo "== Integration tests (Testcontainers: MySQL, Redis, Kafka via docker) =="
mvn -q -B -pl reproduction-persistence,reproduction-worker-service verify -DskipUnitTests=false

echo
echo "All tests passed."
