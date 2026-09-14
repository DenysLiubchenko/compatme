# --- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Leverage Docker layer caching: resolve dependencies before copying sources.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN addgroup --system compatme && adduser --system --ingroup compatme compatme
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R compatme:compatme /app
USER compatme

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
