# Database Design

MySQL is the durable source of truth for job/task/result state; Redis holds only derived,
regenerable, short-lived data (caches, locks, progress). If Redis is lost entirely, every job in
flight can still be finished correctly from MySQL alone (slower — cold cache — but correct).

Full DDL: `reproduction-persistence/src/main/resources/schema.sql` (loaded by docker-compose into
MySQL's `docker-entrypoint-initdb.d`, and by the Testcontainers MySQL integration test).

## Tables

- **`reproduction_job`** — one row per job: status (State pattern enum), strategy, the *stored*
  initial input and options (JSON — see below), `idempotency_key` (unique), `request_fingerprint`
  (SHA-256 of the semantically-relevant request fields, so a key reused with a different body is
  detected), `deadline_at` (for the timeout sweeper), `version` (optimistic locking — the
  orchestrator and its own timeout sweeper both mutate jobs concurrently).
- **`bug_signature`** — 1:1 with job; kept as its own table rather than columns on `reproduction_job`
  because it's optional/sparse and conceptually a separate value object (Specification pattern).
- **`input_field`** — the flattened field universe, one row per leaf path, **materialised once at
  submission** so every later table (dependencies, candidates) can reference fields by a small
  integer index instead of repeating the dotted path string.
- **`input_dependency`** — edges by field index; a normalized many-to-many, not embedded JSON,
  because dependencies are genuinely relational (queried by analysis, joined against fields).
- **`candidate`** + **`candidate_field`** — a candidate is a *set* of fields. We use a real
  many-to-many junction table (`candidate_field`) rather than a JSON array of field indexes,
  specifically so "which candidates contain field X" is an indexed query, not a JSON scan. We
  *do* also keep `canonical_text` on `candidate` (nullable, informational) — storing the full
  canonical JSON is allowed by the assignment for reproducibility/debugging, but the field
  membership itself is relational, per the "do NOT store the complete candidate repeatedly as
  giant JSON if relational modeling is more appropriate" instruction.
- **`evaluation_result`** — the audit trail of every (candidate, task, attempt). This is the
  highest-volume table; see indexing below.
- **`reduction_step`** — the human-readable "how did we get here" trail exposed by `GET
  .../steps`.
- **`worker_task`** — the task graph: one row per (job, ban-set) task, with `attempt`,
  `lease_expires_at` (task ownership/timeout), `version` (optimistic locking — a worker, the
  result handler, and the sweeper can all touch the same row).
- **`job_attempt`** — one row per SUBMIT/RESUME of a job, for audit ("this job was resumed twice,
  here's when and with what outcome").

`reproduction_job.initial_input_json` / `options_json` / `result_json` and
`worker_task.banned_json` / `result_json` are the deliberate JSON exceptions: the *input* is
arbitrary, user-supplied, nested JSON with no fixed schema (modeling it relationally would mean
reinventing a JSON column with extra steps), and `result_json` is a computed, read-mostly
snapshot that's always read whole (never queried by sub-field) — a table per queryable sub-field
would add joins for a benefit nothing uses.

## Indexes

| Index | Why |
|---|---|
| `reproduction_job(status, created_at)` | job listing / monitoring by status |
| `reproduction_job(status, deadline_at)` | the timeout sweeper's exact query |
| `reproduction_job(idempotency_key)` UNIQUE | the idempotency guarantee itself |
| `candidate(job_id, candidate_hash)` UNIQUE | the cache-and-dedup key; `findByJobIdAndCandidateHash` is on the evaluation-recording hot path |
| `candidate(candidate_hash)` | cross-job lookup ("has this exact candidate been seen before, anywhere") |
| `evaluation_result(job_id, candidate_id, task_id, attempt)` UNIQUE | idempotent event recording — the whole point of the constraint is that redelivery is a no-op |
| `evaluation_result(job_id, created_at)` | paged `GET .../evaluations` |
| `worker_task(status, lease_expires_at)` | the sweeper's exact query for expired leases, across *all* jobs |
| `worker_task(job_id, status)` | "are there outstanding tasks for this job" — checked after every result |
| `job_attempt(job_id, attempt_no)` UNIQUE | attempt numbering must be gap-free and race-free |

`candidate_hash` has a **uniqueness constraint per job** (not globally) because the same candidate
can legitimately be evaluated as part of different jobs (different bug signatures, different
initial inputs that happen to share a sub-candidate) with different results.

## Why not one giant `candidate` JSON blob

The assignment explicitly warns against this, and it would also break the actual query patterns
this system needs: "how many evaluations has this job run", "which candidates does field X appear
in", "is this exact (job, hash) pair already evaluated" are all indexed relational lookups on the
hot path (`EvaluationRecorder`, `CandidateEvaluator`'s cache), not analytics queries that would
tolerate scanning JSON.
