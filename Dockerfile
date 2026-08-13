# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so a source-only change doesn't
# re-download the whole repository on every build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -q clean package -DskipTests

# ---- Layer extraction ----
# Spring Boot jars are layered by default (deps / snapshot-deps / loader / app),
# so a rebuild after an app-only code change only pushes a small new layer
# instead of the whole fat jar.
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
COPY --from=build /build/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

USER spring:spring
EXPOSE 8080

# Container-aware JVM defaults: caps heap to a fraction of the container's
# memory limit instead of the host's, so this doesn't get OOM-killed under a
# tight Docker/K8s memory limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# app.jar is a thin jar (Main-Class + a manifest Class-Path pointing at ./lib/*.jar,
# which the dependencies layer above populates) — no custom Boot loader needed here.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
