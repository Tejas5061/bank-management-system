# syntax=docker/dockerfile:1

# ---- build: compile and split the fat jar into cache-friendly layers ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies change far less often than code: resolve them in their own layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Plain jars (app.jar + lib/), not the nested launcher layout: Class Data Sharing below only
# archives classes loaded from ordinary jar files.
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/bank-management-system-1.0.0.jar extract \
      --layers --application-filename app.jar --destination target/extracted

# ---- runtime: JRE only, non-root ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system bank \
 && useradd --system --gid bank --home-dir /app --shell /usr/sbin/nologin bank
WORKDIR /app

# Least-changing layers first, so a code change only rebuilds the small last layers.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

# Class Data Sharing: start the app once here and archive every class it loaded, so real starts map
# them instead of parsing jars again (measured: ~25% less startup CPU, most of a cold start on a
# 0.1-CPU free instance). No database exists at build time, so Flyway is off and Hibernate is told
# not to connect; spring.context.exit stops right after the context refreshes. The JWT value is a
# throwaway for this run only: the real key always comes from the environment.
RUN SPRING_PROFILES_ACTIVE=prod,demo DEMO_RESET_ON_STARTUP=false \
    DB_URL=jdbc:mysql://localhost:1/none DB_USERNAME=none DB_PASSWORD=none \
    JWT_SECRET=Y2RzLXRyYWluaW5nLXJ1bi1vbmx5LW5vdC1hLXJlYWwtc2lnbmluZy1rZXk= \
    java -XX:ArchiveClassesAtExit=app.jsa '-Xlog:cds*=error' -Dspring.context.exit=onRefresh \
         -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
         -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
         -Dspring.jpa.database-platform=org.hibernate.dialect.MySQLDialect \
         -jar app.jar \
 && test -s app.jsa

USER bank
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health/readiness || exit 1
# JVM flags live in an env var so a host can replace them (render.yaml tunes them for 512 MB).
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
