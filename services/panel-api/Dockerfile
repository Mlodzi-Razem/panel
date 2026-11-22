# panel-api — image for the STAGE environment (deploy/stage).
#
# Build (repo root as context, worktree /Users/nepose/Projects/panel/panel-stage):
#   docker build -f services/panel-api/Dockerfile \
#                -t panel-api:$(git rev-parse --short HEAD) \
#                --build-arg GIT_TAG=$(git rev-parse --short HEAD) .
#
# Kept compatible with the LEGACY builder (no BuildKit-only syntax: no `--mount`,
# no ` COPY --chmod`, no heredocs), because this Mac currently has Docker CLI
# without the buildx plugin. Maven-layer caching is provided by the classic
# `dependency:go-offline` layer instead of a BuildKit cache mount: it stays
# warm across builds until any pom.xml changes.
#
# The builder stage runs on the TARGET platform. Java bytecode is arch-neutral,
# so cross-building mostly costs emulation time in `apt`/`mvn`; with the legacy
# builder a cross-platform build additionally requires binfmt emulation
# (see deploy/stage/README.md — "Architektura").

# ---------------------------------------------------------------------------
# 1. Build: multi-module Maven reactor, only the panel-api module.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS builder

# Release provenance: short git hash passed by the release script (optional).
ARG GIT_TAG=unknown
ENV MAVEN_OPTS="-Xmx1024m"

RUN mkdir -p /mrpanel

# Warm dependency cache layer: all reactor poms copied first, so dependency
# downloads are re-run only when a pom.xml changes (not on every source edit).
COPY pom.xml /mrpanel/
COPY docs/pom.xml /mrpanel/docs/
COPY libraries/pom.xml /mrpanel/libraries/
COPY services/pom.xml /mrpanel/services/
COPY services/panel-api/pom.xml /mrpanel/services/panel-api/
COPY services/panel-web/pom.xml /mrpanel/services/panel-web/
COPY cloud/pom.xml /mrpanel/cloud/
COPY cloud/chart/pom.xml /mrpanel/cloud/chart/
COPY cloud/chart-schema/pom.xml /mrpanel/cloud/chart-schema/

WORKDIR /mrpanel
RUN mvn --batch-mode \
        -pl services/panel-api \
        -am \
        dependency:go-offline \
        -DskipTests

# Sources and everything else. Root .dockerignore trims target/node_modules.
COPY . /mrpanel/

RUN mvn --batch-mode \
        -pl services/panel-api \
        -am \
        package \
        -DskipTests

# Spring Boot layered jar → image layers that cache well (unchanged
# dependencies are shared between releases).
RUN mkdir -p /mrpanel-out/extracted && \
    cd /mrpanel/services/panel-api && \
    mv target/panel-api-*.jar /mrpanel-out/application.jar && \
    cd /mrpanel-out && \
    java -Djarmode=tools \
         -jar application.jar \
          extract --layers \
          --launcher \
          --destination extracted

# ---------------------------------------------------------------------------
# 2. Runtime: JRE 25, non-root user, curl + jq for the healthcheck script.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-jammy

ARG GIT_TAG=unknown
LABEL org.opencontainers.image.vendor="Stowarzyszenie Młodzi Razem"
LABEL org.mlodzirazem.panel.service="panel-api"
LABEL org.mlodzirazem.panel.stage.release="${GIT_TAG}"

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates curl jq && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 mrpanel && \
    useradd --system --uid 10001 --gid mrpanel --no-create-home \
        --shell /usr/sbin/nologin mrpanel

WORKDIR /application

# Default JAR layers (see the extraction above).
COPY --from=builder --chown=mrpanel:mrpanel /mrpanel-out/extracted/dependencies ./
COPY --from=builder --chown=mrpanel:mrpanel /mrpanel-out/extracted/snapshot-dependencies ./
COPY --from=builder --chown=mrpanel:mrpanel /mrpanel-out/extracted/spring-boot-loader ./
COPY --from=builder --chown=mrpanel:mrpanel /mrpanel-out/extracted/application ./

# Entrypoint and healthcheck reused from the repo's own scripts
# (services/panel-api/docker/). They are invoked through `sh`, so the source
# files do not need the executable bit in the layer.
USER 10001:10001
COPY --chown=mrpanel:mrpanel services/panel-api/docker/launch.sh ./
COPY --chown=mrpanel:mrpanel services/panel-api/docker/healthcheck.sh ./

# JAVA_OPTS is a knob for small VPS tuning (see /mrpanel launch flags:
# MaxRAMPercentage=80 respects container memory limits).
ENV JAVA_OPTS=""

# API (server.port) and actuator (management.server.port).
EXPOSE 8080
EXPOSE 9080

ENTRYPOINT ["/bin/sh", "/application/launch.sh"]
HEALTHCHECK --interval=10s \
            --timeout=5s \
            --retries=10 \
            --start-period=5s \
    CMD ["sh", "/application/healthcheck.sh"]
