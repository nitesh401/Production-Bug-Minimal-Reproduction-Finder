#!/usr/bin/env bash
# End-to-end demo against the live docker-compose stack.
# Usage: ./scripts/demo.sh [SCENARIO] [STRATEGY]
#   SCENARIO: SIMPLE_AND (default) | OR_BRANCH | NESTED_JSON | DEPENDENCY_TRAP | FLAKY | MULTI_MINIMAL
#   STRATEGY: HYBRID (default) | DDMIN | DEPENDENCY_AWARE | PRIORITY
set -euo pipefail
API=${API_URL:-http://localhost:8080}
SCENARIO=${1:-SIMPLE_AND}
STRATEGY=${2:-HYBRID}

echo "== 1. Original production-like input (27 fields) =="
INPUT=$(cat <<'JSON'
{
  "amount": 15000, "currency": "INR", "customerType": "PREMIUM", "country": "IN",
  "device": "MOBILE", "language": "en", "timezone": "IST", "coupon": "WELCOME100",
  "taxType": "GST", "paymentMethod": "UPI", "orderId": "ORD-88231", "merchantId": "M-4412",
  "featureFlags": {"FAST_PATH": true, "NEW_UI": false, "DARK_MODE": true, "BETA_CHECKOUT": false},
  "metadata": {"source": "mobile", "campaign": "DIWALI", "referrer": "push-notification"},
  "user": {"id": "U-1029", "type": "PREMIUM", "subscription": {"level": 4}},
  "shipping": {"method": "EXPRESS", "city": "Hyderabad", "pincode": "500081"},
  "session": {"id": "s-991", "retries": 2}
}
JSON
)
echo "Fields: 27  Theoretical search space: 2^27 = 134,217,728"

echo
echo "== 2. Submit reproduction job (scenario=$SCENARIO strategy=$STRATEGY) =="
BODY=$(cat <<JSON
{"name":"payment-500-investigation","initialInput":$INPUT,"scenarioId":"$SCENARIO",
 "bugSignature":{"httpStatus":500,"errorCode":"PAYMENT_ROUTE_FAILURE"},
 "evaluationAttempts":5,"minimumReproductionRate":0.6,"strategy":"$STRATEGY"}
JSON
)
RESP=$(curl -s -X POST "$API/api/v1/reproduction/jobs" -H 'Content-Type: application/json' -H "Idempotency-Key: demo-$(date +%s)" -d "$BODY")
JOB_ID=$(echo "$RESP" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
echo "Job: $JOB_ID"

echo
echo "== 3. Poll progress (Kafka tasks -> workers evaluating in parallel) =="
for i in $(seq 1 60); do
  P=$(curl -s "$API/api/v1/reproduction/jobs/$JOB_ID/progress")
  STATE=$(echo "$P" | grep -o '"state":"[^"]*"' | head -1 | cut -d'"' -f4)
  echo "  [$i] $P"
  [[ "$STATE" =~ ^(COMPLETED|PARTIAL|FAILED|TIMED_OUT)$ ]] && break
  sleep 2
done

echo
echo "== 4. Dependency graph for this input =="
curl -s -X POST "$API/api/v1/analysis/dependencies" -H 'Content-Type: application/json' -d "{\"input\":$INPUT}" 2>/dev/null \
  || curl -s -X POST http://localhost:8083/api/v1/analysis/dependencies -H 'Content-Type: application/json' -d "{\"input\":$INPUT}"

echo
echo "== 5. Reduction steps =="
curl -s "$API/api/v1/reproduction/jobs/$JOB_ID/steps"

echo
echo "== 6. Final minimal reproduction =="
curl -s "$API/api/v1/reproduction/jobs/$JOB_ID/result" | tee /tmp/reproduction-result.json
echo
echo "(see /tmp/reproduction-result.json for the full JSON: minimalCandidates, reductionPercent, candidatesEvaluated, cacheHits...)"
