package com.example.reproduction.simulator;

import com.example.reproduction.domain.ResponseObservation;
import com.example.reproduction.util.Hashing;

import java.util.Map;

/** Evaluates a scenario against one request. Pure and deterministic given (candidateHash, attempt). */
public final class ScenarioEngine {
    private ScenarioEngine() {}

    public static ResponseObservation run(BugScenario scenario, Map<String, Object> request, String candidateHash, int attempt) {
        for (BugScenario.Rule r : scenario.rules()) {
            if (!r.when().test(request)) continue;
            if (r.probability() < 1.0) {
                double u = Hashing.unitInterval(scenario.id() + ":" + candidateHash + ":" + attempt);
                if (u >= r.probability()) continue; // flaky rule did not fire this time
            }
            long latency = 20 + request.toString().length() % 30;
            return new ResponseObservation(r.status(), r.errorCode(), "{\"errorCode\":\"" + r.errorCode() + "\",\"message\":\"" + r.message() + "\"}", latency, null);
        }
        return new ResponseObservation(200, null, "{\"status\":\"OK\"}", 15 + request.toString().length() % 20, null);
    }
}
