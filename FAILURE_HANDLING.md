# Failure Handling

Every scenario the assignment lists, and where it's handled:

| Scenario | Handling |
|---|---|
| **Worker crash** | Task's Redis lock expires (lease) → `TimeoutSweeper.sweepExpiredTasks` (orchestrator, every 5s) sees the lease expired for a still-`PENDING`/`RUNNING` task and calls `retryOrGiveUp`: re-publish with `attempt+1`, or mark `DEAD` (and publish to DLQ) after `maxTaskAttempts`. |
| **Kafka duplicate delivery** | See KAFKA.md "Idempotent consumers" — every consumer (worker task, orchestrator result/evaluation/command) is a documented no-op on redelivery of already-applied work. |
| **Kafka consumer restart** | `enable-auto-commit=false` + `ack-mode=record`: an in-flight message at restart time is redelivered, not lost — and, per the above, redelivery is safe. |
| **Redis unavailable** | Every Redis call site (`RedisEvaluationCache`, `ProgressTracker`, `TaskDeduplicator`, `CancellationProbe`, `JobContextClient`, `TimeoutSweeper`'s lock) catches `DataAccessException` and **degrades**: cache miss (search still correct, just slower), progress not updated (job still completes), lock fails open (execute anyway), lease sweep still protected by MySQL optimistic locking. Nothing throws up to the caller. `redis_unavailable` metric counts degradations. |
| **MySQL unavailable** | Orchestrator/api-service calls fail loudly (this is *the* source of truth — degrading silently would be wrong): `TimeoutSweeper.sweep` catches `DataAccessException` and just retries next tick; Kafka listeners let the exception propagate to the platform error handler (in-process retry, then DLQ) rather than swallowing it. |
| **Simulator unavailable** | `HttpBugOracle` classifies connection failures / 502-504 as `SYSTEM_ERROR`, timeouts as `TIMEOUT` — both thrown as `EvaluationInfrastructureException`, retried up to `maxInfraRetries` with exponential back-off inside `CandidateEvaluator`, and **never** interpreted as "does not reproduce" (ALGORITHM.md §7). If retries are exhausted, the candidate's evaluation is `INCONCLUSIVE`/`TIMEOUT`/`SYSTEM_ERROR`, which ddmin treats as no-information, not "bug gone" (`DdminTest.inconclusiveIsNeverTreatedAsBugGone`). |
| **Candidate evaluation timeout** | `HttpBugOracle` uses a client-side timeout (`worker.evaluation-timeout`, default 5s) via `WebClient...timeout(...)`, converted to `EvaluationInfrastructureException(TIMEOUT, ...)` — never silently treated as pass/fail. |
| **Orchestrator restart** | All orchestrator state is in MySQL; in-memory state is a de-normalized cache only (Redis `JobContext`, rebuildable from MySQL — see `JobContextService.get`). On restart, `TimeoutSweeper` picks up expired leases and stuck `ANALYZING` jobs (see next row) exactly as if a *different* orchestrator instance had crashed — there is no special-cased "my own restart" logic because none is needed. |
| **Duplicate job submission** | `Idempotency-Key` (api-service) is the primary guard; independently, `JobOrchestrator.handleSubmit` only acts on a `PENDING` job (a redelivered `SUBMIT` for an already-`SEARCHING` job is a no-op). |
| **Partial job completion** | Budget exhaustion (`maxEvaluations`/`maxExecutionTime`) is a first-class `StopReason`, not an error: the job finishes as `PARTIAL` with whatever minimal-so-far candidates were found, each explicitly marked `provenMinimal=false`. |
| **Stale worker task** | A task whose lease expired while a worker was *still* silently working on it (network partition, not a real crash) can race a late `TaskResult` against the sweeper's re-publish. `JobOrchestrator.handleResult` accepts a result for an attempt older than the task's current attempt (counted via `results.stale.attempt`) — it's still correct information (evaluation is deterministic given the same candidate/scenario), just possibly redundant with a newer attempt. |
| **Poison message** | `PoisonMessageException` (malformed payload — missing required fields, unknown job id) is registered as **non-retryable** on every listener's error handler, so it goes straight to the DLQ instead of being retried 3 times for nothing. |
| **DLQ message** | Not auto-consumed (this is a human/ops triage queue by design, per "the algorithm is the heart of the project" — DLQ replay tooling would be pure CRUD). `DeadLetterPublishingRecoverer` preserves the original topic/partition/offset/exception in the DLQ record for triage. |

## Crash recovery for the two-transaction SUBMIT flow

`handleSubmit` deliberately spans two DB transactions with a network call (to analysis-service) in
between: fields are materialised and the job moves to `ANALYZING` in transaction 1; after the
analysis call, dependencies are persisted and the job moves to `SEARCHING` (+ the primary task is
published) in transaction 2. If the orchestrator crashes between them, the job is stuck in
`ANALYZING`. `TimeoutSweeper.sweepStuckAnalyzing` detects any job in `ANALYZING` untouched for
`orchestrator.analyzing-stuck-after` (default 60s) and resets it to `PENDING`, from which
`handleSubmit` is simply called again — safe, because transaction 1 is itself idempotent
(`if (fields.countByJobId(jobId) == 0)`).

## What "fail open" vs. "fail closed" means here

- **Fail open** (continue, possibly slower/less-informed): Redis of any kind, analysis-service
  unavailability (orchestrator degrades to "no explicit dependencies, no priority hints" —
  `AnalysisClient` — the search is still *correct*, the built-in default dependency rules still
  apply worker-side, just less informed).
- **Fail closed** (stop, surface the error): MySQL unavailability for the orchestrator/api-service
  (there is no correct way to proceed without the source of truth), oracle infrastructure failures
  (never silently treated as a conclusive result), poison messages (never retried into a loop).
