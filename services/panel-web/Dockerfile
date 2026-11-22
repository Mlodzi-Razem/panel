# panel-web — image for the STAGE environment (deploy/stage).
#
# Build (context = the panel-web service directory, not the repo root — the
# Next.js build needs nothing outside services/panel-web):
#   docker build -f services/panel-web/Dockerfile \
#                -t panel-web:$(git rev-parse --short HEAD) \
#                --build-arg GIT_TAG=$(git rev-parse --short HEAD) \
#                services/panel-web
#
# Runtime decision: `output: 'standalone'` IS set in next.config.ts, verified —
# `next build` produces .next/standalone with a node server.js. That is the
# lightest supported runtime: node:24-alpine + standalone output + .next/static
# (~150 MB instead of a full node_modules copy). There is no `public/` directory
# in this app (favicons and assets live in `app/` and `assets/`, bundled by
# Next), so per the Next standalone docs only `.next/standalone` and
# `.next/static` are copied; nothing else is needed at runtime.
#
# The builder runs on the TARGET platform on purpose: `npm ci` downloads
# platform-specific binaries (@next/swc etc.), so cross-arch builds need
# emulation (binfmt) and are slower — see deploy/stage/README.md ("Architektura").
#
# Kept compatible with the LEGACY builder (no BuildKit-only syntax).

# ---------------------------------------------------------------------------
# 1. Build: Node 24 LTS (same line the Maven build pins in the root pom).
# ---------------------------------------------------------------------------
FROM node:24-alpine AS builder

ARG GIT_TAG=unknown
ENV NEXT_TELEMETRY_DISABLED=1

# musl ↔ glibc shim used by some prebuilt binaries.
RUN apk add --no-cache libc6-compat

# Dependency layer: re-run only when the lockfile changes.
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Runtime build-time env: config/env.ts accepts a build-phase placeholder for
# the required variables, so no real values are baked into the image at this
# stage (NEXT_PHASE=phase-production-build is set by `next build` itself).
RUN npm run build

# ---------------------------------------------------------------------------
# 2. Runtime: standalone Next.js server, non-root user.
# ---------------------------------------------------------------------------
FROM node:24-alpine

ARG GIT_TAG=unknown
LABEL org.opencontainers.image.vendor="Stowarzyszenie Młodzi Razem"
LABEL org.mlodzirazem.panel.service="panel-web"
LABEL org.mlodzirazem.panel.stage.release="${GIT_TAG}"

RUN apk add --no-cache libc6-compat && \
    addgroup -S -g 10001 mrpanel && \
    adduser -S -u 10001 -G mrpanel -D -H mrpanel

WORKDIR /application

COPY --from=builder --chown=mrpanel:mrpanel /app/.next/standalone ./
COPY --from=builder --chown=mrpanel:mrpanel /app/.next/static ./.next/static

ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1

USER 10001:10001

EXPOSE 3000

# busybox wget is present in alpine; /api/health returns {"status":"ok"}.
HEALTHCHECK --interval=10s \
            --timeout=5s \
            --retries=10 \
            --start-period=5s \
    CMD wget -q -O - http://localhost:3000/api/health | grep -q '"ok"' || exit 1

CMD ["node", "server.js"]
