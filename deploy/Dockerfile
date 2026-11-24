# FraudShield API - production image (roadmap 9.1)
# Build context = backend/ :  docker build -f deploy/Dockerfile backend   (docker-compose.prod.yml does this)
# Tests are NOT run here: CI runs `mvn verify` (unit + integration + coverage gate) before an image is built.

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -Daether.connector.http.retryHandler.count=5 -Daether.connector.requestTimeout=600000 dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -Daether.connector.http.retryHandler.count=5 -Dmaven.test.skip=true -Djacoco.skip=true package \
    && cp target/fraudshield-backend-*.jar /app.jar

# ---- run ----
# glibc base (not alpine): ONNX Runtime ships glibc native libraries.
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 fraudshield \
    && useradd --system --uid 10001 --gid fraudshield --home-dir /app --shell /usr/sbin/nologin fraudshield
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# blocklists/ (Phase 18 signed lists) is mounted here; created so the app starts even without it.
RUN mkdir -p /app/blocklists && chown -R fraudshield:fraudshield /app
USER fraudshield

ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
