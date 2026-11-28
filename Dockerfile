# syntax=docker/dockerfile:1

# ---------- build stage ----------
# The official Maven image provides Maven 3.9.16 on Eclipse Temurin JDK 21, so
# the build does not use the Maven Wrapper: mvnw could not download Maven in
# Render's build environment.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src

# Maven Central answers HTTP 429 under load in Render's build environment, so
# route "central" through Google's Asia-Pacific Maven Central mirror. The
# settings file is generated here, so the project needs no extra file.
RUN mkdir -p /root/.m2 \
 && printf '%s\n' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
    '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
    '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>google-maven-central-asia</id>' \
    '      <name>Google Maven Central Asia-Pacific mirror</name>' \
    '      <url>https://maven-central-asia.storage-download.googleapis.com/maven2/</url>' \
    '      <mirrorOf>central</mirrorOf>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' \
    > /root/.m2/settings.xml

# Tests are skipped here on purpose: they need an embedded PostgreSQL that
# downloads database binaries, and CI already runs "./mvnw -B verify".
RUN mvn -s /root/.m2/settings.xml -B -DskipTests package

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
