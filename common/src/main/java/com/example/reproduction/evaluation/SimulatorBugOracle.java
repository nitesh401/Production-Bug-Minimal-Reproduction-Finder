package com.example.reproduction.evaluation;

import com.example.reproduction.domain.*;
import com.example.reproduction.simulator.BugScenario;
import com.example.reproduction.simulator.ScenarioEngine;

/** In-process oracle: runs a {@link BugScenario} directly (no network). Used by tests, demo and CLI. */
public final class SimulatorBugOracle implements BugOracle {
    private final BugScenario scenario;
    private final BugSignatureEvaluator signature;

    public SimulatorBugOracle(BugScenario scenario, BugSignature signature) {
        this.scenario = scenario;
        this.signature = new BugSignatureEvaluator(signature);
    }

    @Override
    public EvaluationResult evaluate(Candidate candidate, int attempt) {
        ResponseObservation o = ScenarioEngine.run(scenario, candidate.nested(), candidate.hash(), attempt);
        return EvaluationResult.single(signature.matches(o) ? EvaluationStatus.REPRODUCES_BUG : EvaluationStatus.DOES_NOT_REPRODUCE,
                o.latencyMillis(), "HTTP " + o.status() + (o.errorCode() == null ? "" : " " + o.errorCode()));
    }
}
