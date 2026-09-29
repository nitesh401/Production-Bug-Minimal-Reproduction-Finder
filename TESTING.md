# Testing

## What was actually compiled and run, and how

This sandbox had **no network access** (Maven Central, npm, apt were all unreachable —
`curl` to `repo.maven.apache.org` returns `403 host_not_allowed`), so Maven itself could never
download Spring Boot / Kafka / Redis / MySQL client jars here. Two different levels of rigor
follow directly from that constraint:

1. **`common` (the entire algorithm core) — fully compiled and tested, repeatedly, in this
   sandbox.** It has zero third-party dependencies (JDK 21 standard library only), so it was
   compiled directly with `javac` and its 45 JUnit 5 tests were run against a small
   JUnit-5-annotation-compatible reflective runner (since `junit-jupiter` itself isn't
   fetchable either) covering `@Test`, all `assert*` methods used, and `assertThrows`. **All 45
   tests pass**, and were re-run 8 times back-to-back specifically to catch flakiness in the
   parallel-evaluation code paths (`CandidateEvaluatorTest`'s concurrent-dedup test,
   `EvaluatorUnitTester`'s parallel batch testing) — zero failures across all runs.
2. **The 5 Spring Boot services — written but not compiled in this sandbox.** They're built on
   `common`'s verified API surface, follow conservative, idiomatic Spring Boot 3.3 / Spring Kafka
   / Spring Data JPA patterns, and their tests are designed to be genuinely meaningful (real
   `MockWebServer` HTTP interactions, real `Mockito` behavior verification, real Testcontainers
   for Redis/MySQL where a real service matters) rather than compile-smoke-tests — but **you must
   run `mvn clean test` locally as the first real compilation** and fix forward from there.
   Given the size of this codebase, some import or signature mismatch on first compile would not
   be surprising; none of it is architecturally deep to fix.

## Test inventory

### `common` (verified — see above)

| Class | Covers |
|---|---|
| `CanonicalizationTest` | canonical JSON, SHA-256 hashing, field flatten/unflatten round-trip |
| `GraphAlgorithmsTest` | Tarjan SCC (cycles, singletons, reverse-topological ids), condensation criticality, weak components, transitive closure, the full `DependencyAnalyzer` pipeline |
| `DdminTest` | single/scattered culprits, empty-input bugs, no-duplicate-evaluation, dependency-normalizer correctness, **inconclusive-never-means-bug-gone**, budget exhaustion, priority ranking, chunk splitting |
| `DominanceIndexTest` | superset/subset inference, antichain maintenance, inconclusive results never recorded |
| `CandidateEvaluatorTest` | cache hit/miss, **timeout ≠ does-not-reproduce**, infra-exception retry-then-recover, flaky reproduction-rate threshold, early-exit, **concurrent identical evaluations share one oracle call**, dominance short-circuit, budget enforcement |
| `CandidateScorerTest` | monotonicity of every scoring term |
| `ScenarioDiscoveryTest` | **the algorithm discovers, not hard-codes, every one of the 6 built-in scenarios' documented minimal set(s)**, including the dependency-trap contrast (`DDMIN` fooled, `DEPENDENCY_AWARE`/`PRIORITY`/`HYBRID` not), flaky reproduction rate bounds, all 4 strategies agreeing on a deterministic scenario, parallel and sequential search agreeing |
| `LocalEndToEndTest` | full pipeline in one JVM: 27-field input → dependency analysis → reduction → proven minimal reproduction; **caching actually reduces oracle calls on a second run**; tiny budget yields an honest partial/unproven result instead of failing; a genuinely non-reproducing input is rejected, not silently "reduced" |

Run it yourself:
```bash
mvn -pl common test
```

### Spring services (written, run `mvn test` to execute)

| Module | Test | Style |
|---|---|---|
| `reproduction-simulator-service` | `SimulatorControllerTest` | `@SpringBootTest` + `MockMvc`: built-in scenario returns the right signature, registering/listing/protecting custom scenarios |
| `reproduction-analysis-service` | `AnalysisServiceTest` | plain unit test with a mocked `HistoryService` — no Spring context needed for this logic |
| `reproduction-worker-service` | `HttpBugOracleTest` | `MockWebServer`: matching/non-matching signatures, 503→`SYSTEM_ERROR`, slow response→`TIMEOUT`, unreachable→infra exception, headers (candidate hash/attempt/scenario) sent correctly |
| | `SearchTaskExecutorTest` | `MockWebServer` running the **real** `ScenarioEngine` behind HTTP + mocked Redis/Kafka: the worker's full pipeline (context load → dependency analysis → `ReductionEngine` → HTTP oracle → `TaskResult`) discovers the real minimal reproduction over real HTTP calls |
| | `CandidateTaskListenerTest` | Mockito: duplicate-of-finished-task republishes without re-executing, task-owned-elsewhere is skipped, fresh task executes/remembers/publishes, failure releases the lock and propagates, poison messages are rejected pre-execution |
| | `RedisEvaluationCacheIT` | **Testcontainers** (needs Docker): cross-instance cache visibility through real Redis, and outage degrading to L1-only instead of failing |
| `reproduction-orchestrator-service` | `ResultAssemblerTest` | plain unit test (no Spring) — ranking, dedup of equivalent results, `maxSolutions` truncation, partial results never reported as proven. **This one was additionally compiled and run standalone in this sandbox** (it only depends on `common`), and passes. |
| | `JobStateMachineTest` | plain unit test — every legal/illegal transition |

### Integration / E2E

`scripts/e2e.sh` runs `mvn clean test` (everything above) then `mvn verify` on the modules with
`*IT` Testcontainers classes (`reproduction-worker-service`'s `RedisEvaluationCacheIT`; add MySQL/
Kafka Testcontainers tests the same way — the pattern is established). The **true** end-to-end
demonstration — large input → Kafka → distributed workers → simulator → minimal reproduction — is
`scripts/demo.sh` against the live `docker compose` stack, and is exactly what
`LocalEndToEndTest` proves algorithmically without the infrastructure in between.

## Why a reflective JUnit runner instead of skipping tests

Shipping untested claims about a delta-debugging engine would defeat the point of the exercise.
Since `mvn test` itself wasn't runnable, compiling `common` directly with `javac` and executing
its tests with `java -cp ...` plus ~40 lines of reflection was the highest-fidelity check
available in this environment. It exercises the actual `@Test`-annotated methods, the actual
`assertEquals`/`assertThrows`/etc. semantics used, against the actual compiled classes — not a
paraphrase of the tests.
