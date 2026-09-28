# syntax=docker/dockerfile:1

# ---- build: compile and split the fat jar into cache-friendly layers ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies change far less often than code: resolve them in their own layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/bank-management-system-1.0.0.jar extract --layers --launcher --destination target/extracted

# ---- runtime: JRE only, non-root ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system bank \
 && useradd --system --gid bank --home-dir /app --shell /usr/sbin/nologin bank
WORKDIR /app

# Least-changing layers first, so a code change only rebuilds the small last layer.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER bank
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
