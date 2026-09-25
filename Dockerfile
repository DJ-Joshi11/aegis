# Multi-stage build so the final image only ships a JRE + the built jar,
# not the whole Maven/JDK toolchain.
#
# Uses the official `maven` image (which bundles its own Maven install) for
# the build stage instead of a wrapper script — this repo doesn't commit a
# Maven wrapper (mvnw/.mvn), so `mvn` from the base image is what's actually
# available to run the build.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy just the POM first so dependency resolution is cached across builds
# that only change source files.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Wildcard so this doesn't need updating if the artifact version in pom.xml
# ever changes.
COPY --from=build /app/target/*.jar app.jar

# Render (and similar hosts) inject their own PORT env var; application.properties
# already reads server.port=${PORT:8080}, so this just documents the default.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
