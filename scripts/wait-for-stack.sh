#!/usr/bin/env bash
set -euo pipefail
for url in http://localhost:8084/actuator/health http://localhost:8083/actuator/health http://localhost:8081/actuator/health http://localhost:8080/actuator/health; do
  echo -n "waiting for $url "
  until curl -sf "$url" > /dev/null; do echo -n "."; sleep 2; done
  echo " up"
done
echo "Stack is ready."
