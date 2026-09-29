# Algorithm

## 1. The core problem

Given a failing input `S` (a set of fields) and an oracle `test(subset) -> REPRODUCES |
NOT_REPRODUCES | INCONCLUSIVE`, find a subset `M ⊆ S` such that `test(M) = REPRODUCES` and for
every `f ∈ M`, `test(M \ {f}) != REPRODUCES` (**1-minimality**) — without testing anywhere near
`2^|S|` subsets.

## 2. ddmin (`common/.../algorithm/ddmin/Ddmin.java`)

Classic delta debugging (Zeller & Hildebrandt), adapted:

```
ddmin(S):
  if test(∅) reproduces: return ∅
  n = 2
  while |S| >= 2:
      partition S into n contiguous chunks (by current rank order)
      if some chunk itself reproduces:      S = chunk;               n = 2
      elif some complement (S - chunk) reproduces: S = complement;   n = max(n-1, 2)
      elif n < |S|:                          n = min(|S|, n*2)
      else: break
  # 1-minimality proof: test S - {f} for every f in S
  if all of those fail to reproduce: return (S, proven=true)
  else: continue reducing from the first one that reproduced
```

Improvements over the textbook version (see the class Javadoc for detail):

- a **normalizer** repairs (or drops) any proposed candidate that violates a dependency, so the
  ddmin loop never wastes a test on a malformed input — this is what makes reduction
  *dependency-aware* (§3) rather than just field-level;
- a **rank** function orders units before partitioning, so "likely removable" units land in the
  first partitions and get tried for removal first (§5, `PRIORITY` strategy);
- a **refuted-set** memo avoids re-testing a candidate already known not to reproduce within one
  reduction run;
- batches of candidates go through `UnitTester.testFirst`, which can evaluate them **in parallel**
  while still deterministically picking the lowest-index reproducing candidate — see §6;
- the result carries an **explicit proof**: every single-unit removal from the final set was
  tested and conclusively did not reproduce. If any of those tests come back `INCONCLUSIVE`
  (timeout/error), minimality is **not** claimed — see §7.

### Complexity

- **Brute force**: `O(2^N)` evaluations.
- **ddmin**: not `O(log N)` in general (the prompt explicitly says not to claim that). Best case —
  a single small failure-inducing subset — is close to `O(log N)` rounds. Worst case is `O(N²)`
  evaluations: at each of up to `N` granularity doublings, up to `2N` candidates are tested, and
  every accepted reduction restarts the granularity at `n=2`. In practice, on the 6 built-in
  scenarios (24–27 fields), it takes **30–90 evaluations**, not the ~50k+ a naive halving bound
  would suggest, because caching (§4) and dominance pruning (§4) turn most of the "complement" and
  "proof" tests into cache hits or inferred results rather than oracle calls.

## 3. Dependency-aware reduction (`algorithm/graph/`, `algorithm/search/UnitSpace.java`)

Not all fields are independent: `amount` and `currency` only make sense together; `user.type`
gates `user.subscription.level`. Removing `currency` while keeping `amount` doesn't test "does the
bug need `amount`" — it tests "does the service crash on malformed input", a different (spurious)
signal. `DEPENDENCY_TRAP` in the simulator exists specifically to demonstrate this: plain `DDMIN`
strategy converges on a 1-field "minimal" set that's actually just a null-pointer crash, while
`DEPENDENCY_AWARE`/`PRIORITY`/`HYBRID` do not (`ScenarioDiscoveryTest.scenario4_...`).

- **`Tarjan.scc`** finds strongly connected components in `O(V+E)` — mutually-dependent field
  groups (from `RelatedGroupRule`s, e.g. `amount ↔ currency`) collapse into one SCC.
- **`Condensation`** builds the DAG of SCCs and computes, for each component, its *criticality* =
  number of other components that transitively depend on it, via one bitset-OR pass per component
  in topological order: `O(C · (C/64 + E))`.
- **`WeakComponents`** (union-find, `O((V+E)·α(V))`) finds *independent* reduction groups — fields
  with no dependency relationship to each other at all, which the `HYBRID` strategy removes in
  bulk before doing fine-grained SCC-level reduction.
- **`UnitSpace.scc(...)`** turns SCCs into ddmin "units": one unit per SCC, ordered
  topologically, each unit's prerequisites are its component's prerequisite components.
  `UnitSpace.normalize` drops any unit whose prerequisites aren't all present — this is the
  ddmin `normalizer` from §2, and it's what makes an invalid candidate **structurally
  unrepresentable** rather than merely discouraged.

## 4. Candidate evaluation, caching, dominance (`evaluation/`, `algorithm/search/DominanceIndex.java`)

Every candidate is canonicalised (`Candidate.canonical()`: sorted field paths, each value through
`CanonicalJson` — sorted object keys, normalised numbers so `15000`, `15000.0`, `1.5E4` all
serialise identically) and hashed with SHA-256 (`O(K)` in the serialised size). This hash is the
cache key and the API-visible candidate identifier.

`CandidateEvaluator.evaluate`, in order:

1. **dominance inference** (only under the monotonicity assumption, on by default): if a *subset*
   of this candidate is already known to reproduce, or a *superset* is already known not to,
   infer the result without calling the oracle at all;
2. **cache lookup** (in-process LRU in `common`/tests; two-tier in-process+Redis in the worker) —
   a hit skips the oracle entirely;
3. **in-flight de-duplication** — if another thread is evaluating the exact same candidate right
   now, join its result instead of duplicating the call;
4. **budget check** (`maxEvaluations`, `maxExecutionTime`) — throws `BudgetExhaustedException`,
   which unwinds ddmin cleanly to a `stopReason` rather than crashing the search;
5. run the oracle for `evaluationAttempts` attempts (with early exit once the flakiness decision
   is mathematically settled — see §7), with `maxInfraRetries` exponential-back-off retries per
   attempt for infrastructure failures.

Only **conclusive** results are cached or fed into the dominance index — see §7.

## 5. Search strategies (Strategy pattern, `algorithm/search/*ReductionStrategy.java`)

| Strategy | Units | Notes |
|---|---|---|
| `DDMIN` | one per field | ignores dependencies; may find spurious candidates (§3) |
| `DEPENDENCY_AWARE` | one per SCC | dependency-safe by construction |
| `PRIORITY` | one per SCC | + priority rank: low historical keep-likelihood / low criticality / large payload fields are proposed for removal first |
| `HYBRID` (default) | weak components → SCCs → optional seeded random restarts | group-level pass removes whole irrelevant clusters cheaply, then priority-ordered SCC-level ddmin, then up to `randomRestarts` shuffled-order re-runs (keeping the smallest result) to escape ordering-induced local minima |

`ReductionEngine` runs the chosen strategy once for the primary reduction, then **enumerates
alternative minimal candidates** (§ below) by re-running the strategy under different bans.

### Enumerating multiple minimal reproductions

A bug can have more than one independent trigger (`MULTI_MINIMAL`: `(amount>10000 AND INR AND
PREMIUM) OR (country=IN AND coupon=WELCOME100)`). After finding one 1-minimal set `M`, every
*different* minimal set must exclude at least one field of `M` (otherwise it would be a superset
of `M`, not itself minimal). So `MinimalEnumeration.nextBans` branches into `|M|` new searches,
each banning one more field of `M` from ever being reintroduced. Every set found this way is
minimal globally (not just under its ban), because ddmin's own proof step verified that removing
any of its elements — within the allowed space — stops reproduction. This is a hitting-set-style
search, bounded by `maxSolutions` and `maxTasksPerJob` (distributed: each branch is its own Kafka
task, see `JobOrchestrator.complete`).

## 6. Parallel evaluation

`EvaluatorUnitTester.testFirst` submits every candidate in a ddmin batch to a **bounded** executor
(`ParallelExecutors.bounded`: fixed threads, bounded queue, caller-runs-on-full — so a slow oracle
degrades to serial execution instead of unbounded thread growth) and consumes futures **in index
order**, cancelling later ones as soon as an earlier index reproduces. This keeps the algorithm's
result deterministic (always the lowest-index reproducing candidate, exactly as sequential
`UnitTester.testFirst` would pick) while genuinely running candidates concurrently. At the
distributed level, parallelism also comes from the **task graph**: primary reduction and each
alternative-minimal-candidate branch are independent Kafka tasks that different worker instances
can pick up simultaneously.

## 7. Correctness: five-valued outcomes, never "timeout = bug gone"

`EvaluationStatus` is `REPRODUCES_BUG | DOES_NOT_REPRODUCE | INCONCLUSIVE | TIMEOUT |
SYSTEM_ERROR`. Only the first two are `conclusive()`. This distinction is threaded through the
entire stack:

- `CandidateEvaluator` never caches or dominance-records a non-conclusive result;
- `EvaluatorUnitTester.test` maps non-conclusive statuses to `Verdict.INCONCLUSIVE`, which ddmin
  treats as "no information" — it neither accepts nor refutes the candidate;
- the 1-minimality **proof** step explicitly checks: if any single-field-removal test came back
  inconclusive, `provenMinimal` is `false`, even if the search otherwise looks complete
  (`DdminTest.inconclusiveIsNeverTreatedAsBugGone`).

### Flaky bugs

`evaluationAttempts` + `minimumReproductionRate` turn a boolean oracle into a probabilistic one:
a candidate reproduces if `reproductions / attempts >= minimumReproductionRate` (evaluated with
early exit once the outcome is mathematically settled — e.g. 3 successes out of 5 needed at
`0.6` stops after 3). `CandidateEvaluator.confirm` re-runs **all** attempts (no early exit, no
cache) to report the true empirical rate for the final result. `FLAKY` scenario reproduces with
probability 0.85 per attempt, seeded deterministically by `(scenarioId, candidateHash, attempt)`
so the same candidate is exactly as flaky across every re-evaluation and every service restart.

## 8. Candidate scoring

Lower is better: `score = wField·|fields| + wPayload·payloadBytes + wDep·internalDependencyEdges +
wCost·evaluationMillis + wConf·(1 − reproductionConfidence)` (`CandidateScorer`, weights in
`ScoringWeights`, configurable per job). Field count dominates by default; among equally-sized
candidates, smaller payload and fewer internal dependency edges (simpler candidates) win; lower
empirical reproduction confidence is penalised so a 60%-flaky 3-field candidate doesn't outrank a
100%-reliable 4-field one for no reason.
