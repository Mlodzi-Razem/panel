#!/bin/sh
# ci/deploy-on-vps.sh — the VPS half of the STAGE release (run ON the VPS,
# over ssh, by the GitHub Actions workflow OR by hand for a manual deploy).
#
# Contract: images are already built and pushed to GHCR by CI
# (.github/workflows/stage.yml); this script pulls them, switches the compose
# stack, waits for health and records "last-stable" for scripts/rollback.sh.
#
# Usage:
#   ci/deploy-on-vps.sh --api-image <ref> --web-image <ref> [options]
#     --api-image    full image ref, e.g. ghcr.io/mlodzi-razem/panel-api:sha-6a9b6ff
#     --web-image    full image ref, e.g. ghcr.io/mlodzi-razem/panel-web:sha-6a9b6ff
#     --stage-dir    default /opt/panel-stage
#     --skip-pull    reuse local images (manual/emergency path)
#     --no-wait      do not `up --wait` (compose only; rarely needed)
#
# Prereqs: .env.stage in the stage dir (owner), GHCR login for private images
# (`docker login ghcr.io` — owner's deploy token, RUNBOOK §6), the deploy/stage
# bundle synced by the workflow (compose file, Caddyfile, scripts/).
# Nothing here ever prints secret values.
set -eu

STAGE_DIR="/opt/panel-stage"
API_IMAGE=""
WEB_IMAGE=""
SKIP_PULL="0"
WAIT="1"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --api-image) API_IMAGE="$2"; shift 2 ;;
        --web-image) WEB_IMAGE="$2"; shift 2 ;;
        --stage-dir) STAGE_DIR="$2"; shift 2 ;;
        --skip-pull) SKIP_PULL="1"; shift ;;
        --no-wait) WAIT="0"; shift ;;
        *) echo "Unknown option: $1" >&2; exit 64 ;;
    esac
done

if [ -z "$API_IMAGE" ] || [ -z "$WEB_IMAGE" ]; then
    echo "deploy-on-vps: --api-image and --web-image are required." >&2
    exit 64
fi

cd "$STAGE_DIR"

if [ ! -f .env.stage ]; then
    echo "deploy-on-vps: brak .env.stage w $STAGE_DIR (RUNBOOK §2)." >&2
    exit 66
fi

STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "== deploy-on-vps: sanity compose config =="
docker compose -f docker-compose.stage.yaml \
    --env-file .env.stage config -q

# Record the release in a small env overlay (transparency + rollback source).
# Shell-exported values override --env-file substitution (documented compose
# precedence), so the images below are exactly the ones passed here.
cat > release.env <<EOF
# Written by ci/deploy-on-vps.sh at ${STAMP}; do not edit by hand.
PANEL_API_IMAGE=${API_IMAGE}
PANEL_WEB_IMAGE=${WEB_IMAGE}
EOF

export PANEL_API_IMAGE="$API_IMAGE"
export PANEL_WEB_IMAGE="$WEB_IMAGE"

COMPOSE="docker compose -f docker-compose.stage.yaml --env-file .env.stage"

if [ "$SKIP_PULL" != "1" ]; then
    echo "== deploy-on-vps: pull ${API_IMAGE} + ${WEB_IMAGE} =="
    # shellcheck disable=SC2086
    $COMPOSE pull panel-api panel-web
fi

echo "== deploy-on-vps: up -d --wait =="
if [ "$WAIT" = "1" ]; then
    # shellcheck disable=SC2086
    $COMPOSE up -d --wait --wait-timeout 420
else
    # shellcheck disable=SC2086
    $COMPOSE up -d
fi

echo "== deploy-on-vps: healthcheck =="
if ./scripts/healthcheck.sh; then
    echo "== deploy-on-vps: OK — odnotowuję last-stable =="
    cat > last-stable.txt <<EOF
# Written by ci/deploy-on-vps.sh at ${STAMP}.
PANEL_API_IMAGE=${API_IMAGE}
PANEL_WEB_IMAGE=${WEB_IMAGE}
RELEASED=${STAMP}
EOF
    echo "${STAMP} OK api=${API_IMAGE} web=${WEB_IMAGE}" >> releases.txt
    echo "== deploy-on-vps: SUKCES =="
    exit 0
else
    echo ""                                          >&2
    echo "deploy-on-vps: HEALTHCHECK NIE PRZESZEDŁ."   >&2
    echo "Stack wstał z ${API_IMAGE}, ale nie jest zdrowy."          >&2
    echo "Rollback:  ./scripts/rollback.sh   (wraca do last-stable)" >&2
    echo "Logi:      docker compose -f docker-compose.stage.yaml --env-file .env.stage logs --tail 200" >&2
    exit 10
fi
