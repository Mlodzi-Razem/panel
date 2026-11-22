#!/bin/sh
# rollback.sh — switch STAGE back to an earlier release (run on the VPS).
#
# Usage:
#   ./scripts/rollback.sh                # to the last known-good (last-stable.txt)
#   ./scripts/rollback.sh --to sha-6a9b6ff   # to a specific GHCR tag
#   ./scripts/rollback.sh --to 6a9b6ff       # to a local-tag image (manual path)
#   ./scripts/rollback.sh --list            # show release history and images
#   flags: --yes (skip the confirmation), --stage-dir /opt/panel-stage
#
# Semantics of the pointer files (managed by ci/deploy-on-vps.sh):
#   release.env     images of the CURRENT release (set during deploy)
#   last-stable.txt images of the last release whose healthcheck passed
#                   (NOT updated by a rollback, so you can roll back again)
#   releases.txt    append-only history with OK/ROLLBACK annotations
set -eu

STAGE_DIR="/opt/panel-stage"
TO=""
CONFIRM=""
LIST="0"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --to)        TO="$2"; shift 2 ;;
        --yes)       CONFIRM="--yes"; shift ;;
        --list)      LIST="1"; shift ;;
        --stage-dir) STAGE_DIR="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 64 ;;
    esac
done

cd "$STAGE_DIR"

if [ "$LIST" = "1" ]; then
    echo "== releases.txt (ostatnie 20) =="
    tail -n 20 releases.txt 2>/dev/null || echo "(brak historii)"
    echo ""
    echo "== last-stable.txt =="
    cat last-stable.txt 2>/dev/null || echo "(brak)"
    echo ""
    echo "== obrazy panel-api/panel-web na tym VPS =="
    docker images --format '{{.Repository}}:{{.Tag}}  {{.CreatedSince}}' \
        | grep -E '^(panel-api|panel-web|ghcr.io/.*/panel-(api|web)):' \
        | sort | tail -n 40
    exit 0
fi

# --- resolve the target image refs -------------------------------------------
API_IMAGE=""
WEB_IMAGE=""

if [ -n "$TO" ]; then
    case "$TO" in
        ghcr.io/* | */*:*)
            # Full refs not supported for two images at once via --to single
            # value; require the short-tag form instead.
            echo "Podaj sam TAG (np. sha-6a9b6ff albo 6a9b6ff), nie pełny ref." >&2
            exit 64
            ;;
    esac
    # Derive the image prefix (registry/org or local) from the current release.
    RAW_API="$(grep -E '^PANEL_API_IMAGE=' release.env 2>/dev/null | tail -n1 | cut -d= -f2- || true)"
    PREFIX=""
    if [ -n "$RAW_API" ]; then
        PREFIX="${RAW_API%:*}"          # drop the :tag
        PREFIX="${PREFIX%/panel-api}"   # drop the repository name
    fi
    if [ -n "$PREFIX" ]; then
        API_IMAGE="${PREFIX}/panel-api:${TO}"
        WEB_IMAGE="${PREFIX}/panel-web:${TO}"
    else
        API_IMAGE="panel-api:${TO}"
        WEB_IMAGE="panel-web:${TO}"
    fi
else
    if [ ! -f last-stable.txt ]; then
        echo "Brak last-stable.txt — nie ma do czego wracać. Użyj --to <tag> albo --list." >&2
        exit 66
    fi
    API_IMAGE="$(grep -E '^PANEL_API_IMAGE=' last-stable.txt | tail -n1 | cut -d= -f2-)"
    WEB_IMAGE="$(grep -E '^PANEL_WEB_IMAGE=' last-stable.txt | tail -n1 | cut -d= -f2-)"
    if [ -z "$API_IMAGE" ] || [ -z "$WEB_IMAGE" ]; then
        echo "last-stable.txt nie zawiera obu obrazów (patrz --list)." >&2
        exit 66
    fi
fi

echo "== rollback: cel =="
echo "  api: ${API_IMAGE}"
echo "  web: ${WEB_IMAGE}"

if [ "$CONFIRM" != "--yes" ]; then
    echo "To spowoduje restart aplikacji; baza i konfiguracja zostają bez zmian."
    printf ".kontynuować? [wpisz TAK] "
    read -r ANSWER
    [ "$ANSWER" = "TAK" ] || { echo "Przerwane."; exit 65; }
fi

# --- switch -------------------------------------------------------------------
export PANEL_API_IMAGE="$API_IMAGE"
export PANEL_WEB_IMAGE="$WEB_IMAGE"

COMPOSE="docker compose -f docker-compose.stage.yaml --env-file .env.stage"

echo "== rollback: pull =="
# shellcheck disable=SC2086
$COMPOSE pull panel-api panel-web

echo "== rollback: up -d --wait =="
# shellcheck disable=SC2086
$COMPOSE up -d --wait --wait-timeout 420

echo "== rollback: healthcheck =="
if ./scripts/healthcheck.sh; then
    STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    cat > release.env <<EOF
# Written by scripts/rollback.sh at ${STAMP}.
PANEL_API_IMAGE=${API_IMAGE}
PANEL_WEB_IMAGE=${WEB_IMAGE}
EOF
    echo "${STAMP} ROLLBACK api=${API_IMAGE} web=${WEB_IMAGE}" >> releases.txt
    echo "== rollback: OK — aplikacja działa na poprzednim wydaniu =="
    exit 0
else
    echo "rollback: healthcheck NIE PRZESZEDŁ po powrocie do ${API_IMAGE}." >&2
    echo "Sprawdź: $COMPOSE logs --tail 200" >&2
    exit 10
fi
