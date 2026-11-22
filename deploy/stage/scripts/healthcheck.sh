#!/bin/sh
# healthcheck.sh — end-to-end STAGE health check (run on the VPS).
# Usage: scripts/healthcheck.sh          (from /opt/panel-stage)
# Exits: 0 = OK; 2/3/4/5 = specific failures (see messages).
set -eu

STAGE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$STAGE_DIR"

COMPOSE="docker compose -f docker-compose.stage.yaml --env-file .env.stage"
STAGE_HOST="$(grep -E '^STAGE_HOST=' .env.stage | tail -n1 | cut -d= -f2-)"

echo "== panel-stage: healthcheck (host: ${STAGE_HOST}) =="

# 1. Web przez Caddy z TLS (cała ścieżka DNS→TLS→Caddy→panel-web).
if ! curl -fsS -o /dev/null --max-time 20 "https://${STAGE_HOST}/"; then
    echo "FAIL: https://${STAGE_HOST}/ nie odpowiada (Caddy/TLS/DNS/panel-web)."
    exit 2
fi
echo "OK: web przez Caddy (TLS) odpowiada."

# 2. Health endpoint webu (przez publiczny adres).
WEB_HEALTH="$(curl -fsS --max-time 20 "https://${STAGE_HOST}/api/health" || true)"
case "$WEB_HEALTH" in
    *ok*) echo "OK: /api/health zwraca status ok." ;;
    *) echo "FAIL: /api/health zwraca: ${WEB_HEALTH:-brak odpowiedzi}"; exit 3 ;;
esac

# 3. Actuator panel-api (port 9080, tylko w sieci wewnętrznej).
API_HEALTH="$($COMPOSE exec -T panel-api \
    curl -fsS --max-time 10 http://localhost:9080/actuator/health || true)"
case "$API_HEALTH" in
    *UP*) echo "OK: actuator/health = UP (panel-api żyje)." ;;
    *) echo "FAIL: actuator: ${API_HEALTH:-brak odpowiedzi}"; exit 4 ;;
esac

# 4. Sanity: no container should be stuck restarting.
if [ -n "$($COMPOSE ps --status restarting -q 2>/dev/null)" ]; then
    echo "FAIL: któryś kontener jest w stanie restarting — patrz: $COMPOSE ps"
    exit 5
fi

echo "== panel-stage: WYGLĄDA DOBRZE =="
exit 0
