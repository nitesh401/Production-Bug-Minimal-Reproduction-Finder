# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY reproduction-platform/pom.xml reproduction-platform/pom.xml
COPY reproduction-persistence/pom.xml reproduction-persistence/pom.xml
COPY reproduction-simulator-service/pom.xml reproduction-simulator-service/pom.xml
COPY reproduction-analysis-service/pom.xml reproduction-analysis-service/pom.xml
COPY reproduction-worker-service/pom.xml reproduction-worker-service/pom.xml
COPY reproduction-orchestrator-service/pom.xml reproduction-orchestrator-service/pom.xml
COPY reproduction-api-service/pom.xml reproduction-api-service/pom.xml
RUN mvn -q -B -pl reproduction-analysis-service -am dependency:go-offline || true
COPY common common
COPY reproduction-platform reproduction-platform
COPY reproduction-persistence reproduction-persistence
COPY reproduction-simulator-service reproduction-simulator-service
COPY reproduction-analysis-service reproduction-analysis-service
COPY reproduction-worker-service reproduction-worker-service
COPY reproduction-orchestrator-service reproduction-orchestrator-service
COPY reproduction-api-service reproduction-api-service
RUN mvn -q -B -pl reproduction-analysis-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/reproduction-analysis-service/target/*.jar app.jar
EXPOSE 8080 8081 8082 8083 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
