# Kafka Design

## Topics

| Topic | Partitions | Key | Producer | Consumer |
|---|---|---|---|---|
| `reproduction.jobs` | 3 | `jobId` | api-service | orchestrator |
| `reproduction.analysis` | 3 | `jobId` | orchestrator (job finished) | analysis-service (history) |
| `reproduction.candidates` | 6 | `taskId` | orchestrator | worker |
| `reproduction.candidates.retry` | 6 | `taskId` | orchestrator (attempt > 1) | worker (same listener) |
| `reproduction.evaluations` | 6 | `jobId` | worker | orchestrator (audit trail → MySQL) |
| `reproduction.results` | 3 | `jobId` | worker | orchestrator |
| `reproduction.dlq` | 1 | original key | platform error handler / orchestrator (dead tasks) | ops tooling (not auto-consumed) |

`jobId` keys keep everything about one job's commands/results/analysis in order on one partition.
`taskId` keys on `candidates` spread one job's *tasks* (primary + alternatives) across partitions
so they can be worked on by different consumer instances in parallel — which is the actual
distributed-parallelism mechanism (see ALGORITHM.md §6).

## Message envelopes (`common/.../messaging/`)

Every message carries `jobId`, `taskId`/`candidateId` where applicable, `attempt`, `timestamp`,
and `correlationId` (propagated from the originating HTTP request's `X-Correlation-Id`, or
generated). `CandidateTask` additionally carries `bannedFields` (what this task excludes) and
`deadline` (copied from the job's `deadline_at`, so a worker never runs past a job that's already
timed out even if it never sees a cancel signal).

## Producer configuration

`acks=all` + `enable.idempotence=true` on every producer (api-service, orchestrator, worker):
Kafka's own idempotent-producer feature prevents duplicate writes from producer-side retries.
This is necessary but not sufficient — see "why idempotent consumers" below, because the
*application-level* at-least-once semantics (a message can still be redelivered after a consumer
crash between processing and committing its offset) aren't solved by the producer alone.

## Consumer configuration and failure policy (`reproduction-platform`)

`enable-auto-commit: false`, `ack-mode: record` — the offset commits only after the listener
method returns successfully, so a crash mid-processing causes redelivery, not loss.

`PlatformAutoConfiguration.KafkaFailurePolicy` (shared by every service) wires:

- **`DefaultErrorHandler`** with `ExponentialBackOff(250ms, ×2, max 3 attempts)` — a transient
  failure (Redis blip, momentary MySQL unavailability) is retried in-process before anything goes
  to a retry topic.
- after those retries are exhausted (or immediately, for `PoisonMessageException` /
  `IllegalArgumentException` — never retried), the record is published to `reproduction.dlq` via
  `DeadLetterPublishingRecoverer`, with the original topic/partition/offset and the exception
  logged. Counters: `kafka_retry_count`, `dlq_messages`.
- deserialization failures are handled the same way: consumers use
  `ErrorHandlingDeserializer` wrapping `JsonDeserializer`, so a genuinely malformed/poison message
  (bad JSON, wrong schema) fails at deserialization time and is routed to the DLQ by the same
  error handler, rather than crashing the consumer thread or being silently dropped.

Worker consumers additionally set `max.poll.records=1` and a long `max.poll.interval.ms`
(15 minutes): one task is one long-running search, so "how many tasks in flight" is controlled by
listener `concurrency` (threads), not by batching unrelated tasks into one poll.

## Idempotent consumers / duplicate handling

Kafka is at-least-once. Every consumer is therefore built to be safely re-run:

- **worker** (`CandidateTaskListener` + `TaskDeduplicator`): a Redis lock
  (`task:{id}:{attempt}:lock`, `SETNX` + lease) gives one worker exclusive ownership of one
  attempt; a duplicate delivery of a task whose result is already stored
  (`task:{id}:result`) **re-publishes the stored result instead of re-executing** — no wasted
  computation, and the eventual publish to `reproduction.results` is still guaranteed. If Redis
  itself is down, the worker fails **open** (executes anyway): correctness doesn't depend on the
  lock, only efficiency does, because the *orchestrator's* handling of results is separately
  idempotent (see below) and the evaluation cache/dominance index make a genuine re-run cheap.
- **orchestrator / results** (`JobOrchestrator.handleResult`): a task already `COMPLETED` or
  `DEAD` ignores further results for it (`results.duplicate` counter). Task creation
  (`createAndPublish`) uses a **deterministic task id** (`UUID.nameUUIDFromBytes(jobId + sorted
  bans)`), so creating "the same task" twice (e.g. two workers both branching on the same
  alternative-minimal-candidate) is a no-op via the primary-key constraint.
- **orchestrator / evaluations** (`EvaluationRecorder`): `evaluation_result` has a unique
  constraint on `(job_id, candidate_id, task_id, attempt)`; a redelivered evaluation event is
  detected and skipped before insert.
- **orchestrator / commands** (`handleSubmit`/`handleCancel`/`handleResume`): each checks the
  job's *current* state before acting (`SUBMIT` only proceeds from `PENDING`, `CANCEL`/`RESUME`
  check `terminal()`), so a redelivered command is a no-op if it already took effect.
- **api-service**: `Idempotency-Key` (see README) is the client-facing idempotency guarantee, one
  layer up from Kafka.

## Retry topics vs. in-process retry

Worker-side task failures are **not** immediately sent to `reproduction.candidates.retry`.
Instead, `CandidateTaskListener` releases the Redis lock and rethrows, letting the platform error
handler's in-process back-off retry the *same* delivery a few times first; only tasks that need a
**new attempt number** (decided by the orchestrator, e.g. after a lease expiry it never heard
back from) are explicitly re-published to `.retry`. This keeps "transient blip" (handled without
bothering the orchestrator) separate from "this attempt is dead, try attempt N+1" (an orchestrator
decision, because only it knows `maxTaskAttempts` and whether the *job* should still be
retried at all).
