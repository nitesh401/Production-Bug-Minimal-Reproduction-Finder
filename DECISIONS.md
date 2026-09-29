# Design Decisions

Short "why", one entry per decision that could plausibly have gone another way.

**Why `common` has zero Spring dependencies.** The assignment is explicit: "The core
delta-debugging engine should be testable as a plain Java component without starting Spring
Boot." Practically, this also meant it was the one module fully verifiable in a sandbox with no
Maven Central access (see TESTING.md) — a nice confirmation that the separation pays for itself.

**Why SCCs (not raw dependency edges) are the ddmin unit for dependency-aware strategies.**
ddmin's contract is "remove or keep a unit atomically". A dependency edge `amount → taxType`
means taxType alone is meaningless, but `amount` and `currency` are *mutually* required — an SCC.
Making the SCC itself the unit (rather than post-hoc validating field-level candidates) means an
invalid candidate is **structurally unrepresentable**, not merely rejected after being generated —
cheaper, and it's what makes the `normalizer` in `Ddmin.run` a pure, fast bitset operation instead
of a second oracle-adjacent check.

**Why `HYBRID` runs group-level, then SCC-level, then optional random restarts — in that order.**
Weak-component (group) reduction is the cheapest possible win: whole independent clusters of
irrelevant fields removed in a handful of tests, no dependency bookkeeping needed. SCC-level
priority reduction then does the fine-grained work only on what's left. Random restarts are last
and opt-in (`randomRestarts`, default 0) because they multiply evaluation cost for a benefit
(escaping an ordering-induced local minimum) that the built-in scenarios don't actually need —
`ScenarioDiscoveryTest` finds every documented minimal set at `randomRestarts=0`.

**Why dominance pruning is gated by `assumeMonotonic` (default true) rather than always on.**
"If A ⊇ B and B reproduces, A reproduces" is not universally true for adversarial bugs (a field
that *fixes* the bug when present). It's true for the vast majority of real production bugs
(more broken input → still broken) and is what makes the built-in scenarios evaluate in tens of
calls instead of hundreds. Making it an explicit, named assumption (`SearchConfig.assumeMonotonic`)
rather than baking it in silently means a user with a genuinely non-monotonic bug can turn it off
per job rather than getting silently wrong results.

**Why the orchestrator, not the worker, owns alternative-minimal-candidate enumeration.**
Enumeration (`MinimalEnumeration.nextBans`) is a hitting-set search *over tasks*, not over
candidates within one task — each branch is a full, independent, potentially-parallel reduction.
That's a task-graph decision (how many tasks should exist, bounded by `maxSolutions` /
`maxTasksPerJob`), which is exactly the orchestrator's job per the assignment's own service
boundary ("divide the search problem into phases... schedule reduction tasks").

**Why evaluation events go to Kafka (`reproduction.evaluations`) instead of the worker writing to
MySQL directly.** Keeps MySQL entirely off the worker's hot path (per-candidate writes at
evaluation speed would make the algorithm's own caching pointless) and keeps the audit trail
durable even if the orchestrator is temporarily behind — events queue in Kafka rather than being
dropped or blocking the search.

**Why `JobContext` is cached in Redis but MySQL stays authoritative.** A worker needs the job's
input/config on every task; fetching it from the orchestrator over HTTP for every task adds
latency and coupling. Redis is a pure read-through cache here (`JobContextClient`,
`JobContextService.get`) — its loss only costs one extra HTTP round-trip per task, never
correctness.

**Why idempotency keys are fingerprint-checked, not just existence-checked.** A naive
`idempotency_key → return existing job` would silently swallow a different request that reused a
key by mistake. Fingerprinting the semantically relevant fields (name, input, signature, options
— not e.g. the correlation id) and rejecting a mismatch with `409` turns a subtle bug class into
an explicit, loud client error.

**Why `PARTIAL` is a distinct terminal state from `FAILED`.** A job that ran out of budget but
found *some* reproducing candidate (just not proven-minimal) has a genuinely useful result — an
SRE would rather see "we got it down to 9 fields but ran out of time" than "FAILED". `FAILED` is
reserved for "the full input doesn't even reproduce" or "the primary task itself errored out after
all retries" — cases with no usable candidate at all.

**Why the simulator's bug rules are a Specification-pattern DSL exposed over REST, not hard-coded
Java per scenario.** The assignment asks for "at least 5" scenarios and configurability; a REST
DSL means new scenarios (for ad hoc testing, or a real SRE's specific bug shape) don't require a
code change or redeploy of the simulator, while the 6 built-in scenarios still live as compiled
`BugScenario` constants (`Scenarios.java`) precisely because they're the ones the test suite makes
guarantees about.
