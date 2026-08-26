# syntax=docker/dockerfile:1

# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer: cached until the POM changes.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# Source layer.
COPY config ./config
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21.0.5_11-jre-jammy AS runtime

# curl is used by the container HEALTHCHECK.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 idp \
    && useradd --system --uid 10001 --gid idp --no-create-home --shell /usr/sbin/nologin idp

WORKDIR /app
COPY --from=build --chown=idp:idp /build/target/owaspcheck.jar /app/app.jar

USER idp

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
