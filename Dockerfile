# --- Build stage -----------------------------------------------------------
# Uses the project's own Maven wrapper (mvnw) instead of a separate `maven` base image —
# one fewer image to pull, and guarantees the exact Maven version pinned in .mvn/wrapper.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# mvnw bootstraps its own Maven distribution via curl + unzip; the slim JDK base image
# doesn't include them by default.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Leverage Docker layer caching: resolve dependencies before copying sources.
RUN ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system compatme \
    && adduser --system --ingroup compatme compatme
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R compatme:compatme /app
USER compatme

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl --fail --silent http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
