# API Reference

Base URLs (docker-compose default ports): api-service `http://localhost:8080`,
orchestrator (internal) `http://localhost:8081`, analysis `http://localhost:8083`,
simulator `http://localhost:8084`.

All requests/responses are JSON. Send `X-Correlation-Id` to trace a request through logs across
services (echoed back, or generated if absent — see `platform.logging.CorrelationIdFilter`).

## Reproduction jobs (`reproduction-api-service`)

### `POST /api/v1/reproduction/jobs`

Headers: `Idempotency-Key` (optional, recommended). Resending the same key with the **same** body
returns the original job (200 semantics via a 201 with the existing job); the same key with a
**different** body is rejected `409`.

```json
{
  "name": "payment-500-investigation",
  "initialInput": { "amount": 15000, "currency": "INR", "...": "..." },
  "scenarioId": "SIMPLE_AND",
  "bugSignature": { "httpStatus": 500, "errorCode": "PAYMENT_ROUTE_FAILURE" },
  "evaluationAttempts": 3,
  "minimumReproductionRate": 0.66,
  "maxEvaluations": 5000,
  "maxExecutionSeconds": 300,
  "maxConcurrentEvaluations": 4,
  "maxSolutions": 5,
  "randomRestarts": 0,
  "assumeMonotonic": true,
  "strategy": "HYBRID"
}
```

`bugSignature` fields are all optional but at least a meaningful combination should be set:
`httpStatus`, `errorCode`, `bodyPattern` (regex), `minLatencyMillis`, `exceptionSignature`. All
set fields must match (AND) for a response to count as reproducing.

`strategy` ∈ `DDMIN | DEPENDENCY_AWARE | PRIORITY | HYBRID` (default `HYBRID`).

→ `201 Created`, `Location: /api/v1/reproduction/jobs/{jobId}`

```json
{ "jobId": "...", "name": "...", "status": "PENDING", "strategy": "HYBRID",
  "originalFieldCount": 0, "createdAt": "...", "updatedAt": "...", "startedAt": null,
  "completedAt": null, "errorMessage": null }
```

`originalFieldCount` is filled in once the orchestrator has flattened the input (poll `GET
/{jobId}`).

### `GET /api/v1/reproduction/jobs/{jobId}`

Job metadata and current `status` ∈ `PENDING | ANALYZING | SEARCHING | COMPLETED | PARTIAL |
FAILED | CANCELLED | TIMED_OUT`.

### `GET /api/v1/reproduction/jobs/{jobId}/progress`

```json
{ "jobId": "...", "state": "SEARCHING",
  "details": { "state": "SEARCHING", "tasksTotal": "3", "tasksCompleted": "1",
                "evaluationsRecorded": "42", "cacheHits": "11", "updatedAt": "..." } }
```

Backed by Redis; best-effort (falls back to just `state` from MySQL if Redis is unavailable).

### `GET /api/v1/reproduction/jobs/{jobId}/result`

`409` if the job hasn't produced a result yet. Otherwise:

```json
{
  "originalFieldCount": 27,
  "theoreticalSearchSpace": "134217728",
  "minimalCandidates": [
    { "fieldPaths": ["amount", "currency", "customerType", "featureFlags.FAST_PATH"],
      "candidateHash": "…64 hex chars…",
      "input": { "amount": 15000, "currency": "INR", "customerType": "PREMIUM",
                  "featureFlags": { "FAST_PATH": true } },
      "reproductionRate": 1.0, "confirmationAttempts": 3,
      "score": { "total": 40.03, "fieldCount": 4, "payloadBytes": 103, "dependencyEdges": 1,
                  "confidence": 1.0, "evaluationCostMillis": 15.2 },
      "provenMinimal": true }
  ],
  "candidatesEvaluated": 47, "cacheHits": 19, "reductionPercent": 85.19,
  "reproductionConfidence": 1.0, "equivalentMinimalCandidates": 1,
  "provenMinimal": true, "stopReason": "COMPLETED", "note": null
}
```

### `GET /api/v1/reproduction/jobs/{jobId}/steps`

Ordered reduction audit trail: `{ index, taskId, phase, action, sizeBefore, sizeAfter,
granularity, candidateHash }[]`. `phase` ∈ `group|scc|field|random-restart-N`; `action` ∈
`subset|complement|proof-reduction|reduce-to-empty`.

### `GET /api/v1/reproduction/jobs/{jobId}/evaluations?page=0&size=50`

Paged raw evaluation audit trail: `{ candidateHash, taskId, attempt, status, attemptsRun,
reproductions, reproductionRate, durationMs, source }[]` (`source` ∈ `EVALUATED|CACHE|
INFERRED_BY_DOMINANCE`).

### `POST /api/v1/reproduction/jobs/{jobId}/cancel` → `202 Accepted`

Idempotent; stops running/pending tasks (workers check a Redis flag) and outstanding tasks are
marked dead. The job becomes `CANCELLED`.

### `POST /api/v1/reproduction/jobs/{jobId}/resume` → `202 Accepted`

Only for `FAILED | CANCELLED | TIMED_OUT | PARTIAL` jobs (409 otherwise). Re-publishes any
non-completed / partially-completed tasks with a bumped attempt counter; the job returns to
`SEARCHING`.

## Simulator (`reproduction-simulator-service`)

### `POST /simulate/payment`

The simulated production endpoint. Body = the candidate input (any JSON object). Headers:
`X-Bug-Scenario` (defaults to `SIMULATOR_DEFAULT_SCENARIO` if absent), `X-Attempt` (0-based, seeds
flaky rules), `X-Candidate-Hash` (seeds flaky rules; derived from the body if absent), `X-Chaos`
∈ `TIMEOUT|UNAVAILABLE` to test failure handling. Returns the scenario's configured status/body.

### `POST /api/v1/simulator/scenarios`

Register a custom scenario:

```json
{ "id": "MY_BUG", "description": "...",
  "rules": [{ "anyOf": [[{ "path": "amount", "op": "GT", "value": 10000 },
                          { "path": "currency", "op": "EQ", "value": "INR" }]],
              "status": 500, "errorCode": "MY_ERROR", "probability": 1.0 }] }
```
`anyOf` is a list of clauses-lists (disjunctive normal form: rule matches if ANY inner list has
ALL its clauses true). `op` ∈ `EQ|GT|GTE|STARTS_WITH|PRESENT|ABSENT`. `404` if scenario id starts
with a built-in id; built-ins cannot be overwritten (`409`).

### `GET /api/v1/simulator/scenarios` — list all (built-in + registered).

### `POST /api/v1/simulator/test` — `{scenarioId, input, attempt}` → evaluate once without going
through the reduction engine; useful for sanity-checking a scenario definition.

## Dependency analysis (`reproduction-analysis-service`)

### `POST /api/v1/analysis/dependencies`

```json
{ "input": { "amount": 1, "currency": "INR" } }
```
or `{ "fields": ["amount", "currency"], "explicitDependencies": [{"from":"a","to":"b","reason":"..."}] }`.

→ `{ fields, dependencies, stronglyCoupledGroups, independentGroups, criticalFields, sccCount,
     weakComponentCount, keepHints }`.

### `GET /api/v1/analysis/hints?fields=amount&fields=currency` — keep-likelihood per field.

### `GET /api/v1/analysis/history?field=amount` — `{ field, seen, kept, keepLikelihood }`.
