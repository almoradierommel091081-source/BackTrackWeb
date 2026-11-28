# syntax=docker/dockerfile:1

# ---------- build stage ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# The Maven Wrapper is configured with distributionType=only-script, so mvnw
# downloads and unpacks Apache Maven 3.9.16 itself: curl for the download and
# unzip because the distribution is distributed as a .zip.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Wrapper and POM first, so dependency downloads stay cached when only src/ changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw \
 && ./mvnw -B dependency:go-offline

COPY src ./src

# Tests are skipped here on purpose: they need an embedded PostgreSQL that
# downloads database binaries, and CI already runs "./mvnw -B verify".
RUN ./mvnw -B -DskipTests package

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
