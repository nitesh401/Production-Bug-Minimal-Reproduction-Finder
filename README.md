# Production Bug Minimal Reproduction Finder

Given a production request/state that fails, automatically discover the **smallest** subset of
input/state that still reproduces the bug — and prove that it's minimal. This is [delta debugging]
(ddmin), made dependency-aware, distributed over Kafka, and wrapped in a small set of Spring Boot
microservices around a **pure-Java, Spring-free algorithm core**.

[delta debugging]: https://en.wikipedia.org/wiki/Delta_debugging

## Why this exists

A failing production request can carry 40 query params, a dozen headers, nested JSON, feature
flags, session state... Only a handful of those actually matter. Brute-forcing all subsets is
`2^N` — for 24 fields that's 16.7 million evaluations. This system finds the minimal reproducing
subset in tens of evaluations instead, using:

- **delta debugging** (adaptive binary partitioning, not brute force) — see [ALGORITHM.md](ALGORITHM.md)
- **dependency-aware reduction** (Tarjan SCC, so it never tests malformed inputs) — see [ALGORITHM.md](ALGORITHM.md)
- **caching + dominance pruning** (never evaluate the same or an implied candidate twice)
- **distributed workers** over Kafka, coordinated by an orchestrator, with MySQL as the durable
  source of truth and Redis for hot caches/locks/progress

## The 5 services

| Service | Port | Role |
|---|---|---|
| `reproduction-api-service` | 8080 | REST API, validation, idempotency. **No algorithm code.** |
| `reproduction-orchestrator-service` | 8081 | Job lifecycle, task graph, retries, timeouts, result assembly. |
| `reproduction-worker-service` | 8082 | Runs the delta-debugging engine against the bug oracle. **This is where the algorithm lives.** |
| `reproduction-analysis-service` | 8083 | Dependency graph analysis (Tarjan SCC) + historical field-importance hints. |
| `reproduction-simulator-service` | 8084 | Deterministic, configurable bug simulator (`POST /simulate/payment`) standing in for "production". |

Plus two shared, non-runnable modules: `common` (the entire algorithm, Spring-free, unit-testable
on its own) and `reproduction-platform`/`reproduction-persistence` (shared Kafka/Redis/JPA
infrastructure).

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full picture and a Mermaid diagram.

## Quick start

```bash
docker compose up --build
./scripts/wait-for-stack.sh
./scripts/demo.sh SIMPLE_AND HYBRID
```

That submits a 27-field payment request, watches it get reduced to the 4 fields that actually
matter, and prints the reduction steps and final result. Try the other scenarios:

```bash
./scripts/demo.sh OR_BRANCH HYBRID          # two independent minimal reproductions
./scripts/demo.sh DEPENDENCY_TRAP DDMIN     # plain ddmin gets fooled by a malformed candidate
./scripts/demo.sh DEPENDENCY_TRAP HYBRID    # dependency-aware reduction finds the real bug
./scripts/demo.sh FLAKY HYBRID              # bug reproduces ~85% of the time
```

## Manual walkthrough

```bash
# 1. Submit a job
curl -X POST localhost:8080/api/v1/reproduction/jobs \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: my-first-job' -d '{
  "name": "payment-500-investigation",
  "initialInput": {
    "amount": 15000, "currency": "INR", "customerType": "PREMIUM", "country": "IN",
    "featureFlags": {"FAST_PATH": true, "NEW_UI": false},
    "device": "MOBILE", "language": "en", "timezone": "IST",
    "coupon": "WELCOME100", "metadata": {"source": "mobile", "campaign": "DIWALI"}
  },
  "scenarioId": "SIMPLE_AND",
  "bugSignature": {"httpStatus": 500, "errorCode": "PAYMENT_ROUTE_FAILURE"},
  "evaluationAttempts": 3, "minimumReproductionRate": 0.66, "strategy": "HYBRID"
}'
# => { "jobId": "...", "status": "PENDING", ... }

# 2. Watch it
curl localhost:8080/api/v1/reproduction/jobs/{jobId}/progress

# 3. Get the minimal reproduction
curl localhost:8080/api/v1/reproduction/jobs/{jobId}/result
```

Full API reference: [API.md](API.md).

## Running the pure algorithm core without any infrastructure

The entire delta-debugging engine, dependency graph algorithms, evaluation cache, and 6 bug
scenarios run as plain Java — no Spring, no Docker:

```bash
mvn -pl common -am package
java -cp common/target/common-1.0.0.jar com.example.reproduction.evaluation.ReproductionDemo SIMPLE_AND HYBRID
```

This is also exactly what `LocalEndToEndTest` in `common` exercises (see [TESTING.md](TESTING.md)).

## Building and testing everything

```bash
mvn clean test              # unit + Spring slice tests (no Docker needed for most modules)
./scripts/e2e.sh            # adds Testcontainers integration tests (needs Docker)
```

> **Note on this repository's provenance:** the `common` module (the actual algorithm — ddmin,
> Tarjan SCC, dependency-aware reduction, caching, scoring, the 6 bug scenarios, 45 JUnit tests)
> was built and verified by compiling and running its full test suite repeatedly in a
> network-isolated sandbox (plain `javac` + a minimal JUnit-5-compatible runner, since Maven
> Central wasn't reachable there) — all 45 tests pass deterministically, including under repeated
> runs to catch concurrency flakiness. The Spring Boot service layers (the 5 microservices) were
> written against that verified core and follow idiomatic, conservative Spring Boot 3.3 /
> Spring Kafka / Spring Data patterns, but **could not be compiled in that sandbox** (no network
> access to download Spring artifacts). Run `mvn clean test` locally as the first step — see
> [TESTING.md](TESTING.md) for exactly what has and hasn't been executed, and fix forward from
> there if anything doesn't compile.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — services, data flow, Mermaid diagrams
- [ALGORITHM.md](ALGORITHM.md) — ddmin, dependency-aware reduction, complexity analysis
- [API.md](API.md) — full REST reference
- [DATABASE.md](DATABASE.md) — schema and indexing rationale
- [KAFKA.md](KAFKA.md) — topics, keys, retry/DLQ, idempotency
- [FAILURE_HANDLING.md](FAILURE_HANDLING.md) — every failure scenario and how it's handled
- [TESTING.md](TESTING.md) — what's tested, how, and what's verified vs. not
- [DECISIONS.md](DECISIONS.md) — why things are the way they are
