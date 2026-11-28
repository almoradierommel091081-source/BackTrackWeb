# syntax=docker/dockerfile:1

# ---------- build stage ----------
# The official Maven image provides Maven 3.9.16 on Eclipse Temurin JDK 21, so
# the build does not use the Maven Wrapper: mvnw could not download Maven in
# Render's build environment.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src

# Tests are skipped here on purpose: they need an embedded PostgreSQL that
# downloads database binaries, and CI already runs "./mvnw -B verify".
RUN mvn -B -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --home-dir /app --shell /usr/sbin/nologin app

# Only the repackaged, bootable JAR - never BackTrackWeb-0.0.1-SNAPSHOT.jar.original.
COPY --from=build --chown=app:app \
     /build/target/BackTrackWeb-0.0.1-SNAPSHOT.jar /app/app.jar

USER app
EXPOSE 8080

# PORT is supplied by Render and read through server.port=${PORT:8080}.
# DB_URL, DB_USERNAME and DB_PASSWORD come from Render environment variables;
# no database credentials or defaults belong in this image.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
