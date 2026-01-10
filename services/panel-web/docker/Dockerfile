FROM maven:3.9-eclipse-temurin-25 AS builder
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN mkdir /mrpanel
COPY . /mrpanel

WORKDIR /mrpanel
RUN --mount=type=cache,target=$MAVEN_CONFIG \
    --mount=type=cache,target=/mrpanel/services/panel-web/node_modules \
      mvn --batch-mode \
          --update-snapshots \
          -e \
          -DskipTests \
          --projects services/panel-web \
          --also-make \
          install

FROM node:24-alpine
LABEL vendor="Stowarzyszenie Młodzi Razem"
LABEL org.mlodzirazem.panel.services.id="panel-web"
LABEL org.mlodzirazem.panel.services.version="0.0.0-MRPANEL-SNAPSHOT"

RUN apk add --no-cache curl g++ libc6-compat make py3-pip; \
    addgroup mrpanel; \
    adduser  --ingroup mrpanel --disabled-password mrpanel; \
    apk --purge del apk-tools

USER mrpanel
WORKDIR /application

COPY --from=builder --chmod=544 --chown=mrpanel:mrpanel /mrpanel/services/panel-web/docker/*.sh .
COPY --from=builder --chown=mrpanel:mrpanel /mrpanel/services/panel-web/.next/standalone .
COPY --from=builder --chown=mrpanel:mrpanel /mrpanel/services/panel-web/.next/stati[c] ./.next/static

EXPOSE 8081
ENTRYPOINT ["/bin/sh", "/application/launch.sh"]
HEALTHCHECK --interval=10s \
            --timeout=5s \
            --retries=10 \
    CMD /application/healthcheck.sh