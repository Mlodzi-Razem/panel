#!/bin/sh
# restore.sh — restore a pg_dump archive into STAGE (run on the VPS).
# Usage: scripts/restore.sh backups/panel-stage-YYYYMMDD-HHMMSS.sql.gz [--yes]
#
# Destructive: drops the current schema (all stage data) and loads the dump.
# Applications are stopped for the duration; migrations must then match the
# code version that produced the dump (Flyway checks it at panel-api boot).
set -eu

STAGE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$STAGE_DIR"

COMPOSE="docker compose -f docker-compose.stage.yaml --env-file .env.stage"

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <plik-backup.sql.gz> [--yes]" >&2
    exit 64
fi

FILE="$1"
CONFIRM="${2:-}"

if [ ! -f "$FILE" ]; then
    echo "FAIL: nie ma pliku $FILE" >&2
    exit 66
fi

if [ "$CONFIRM" != "--yes" ]; then
    echo "To SKASUJE i PODMIENI wszystkie dane stage (schemas incl. flyway i audyt)."
    printf ".kontynuować? [wpisz TAK] "
    read -r ANSWER
    [ "$ANSWER" = "TAK" ] || { echo "Przerwane."; exit 65; }
fi

echo "== restore: sprawdzam archiwum =="
gzip -t "$FILE"
echo "OK: archiwum spójne."

echo "== restore: stop aplikacji =="
$COMPOSE stop panel-api panel-web caddy

echo "== restore: czyszczę schematy bazy (public + events [Spring Modulith]) =="
$COMPOSE exec -T postgres \
    sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
        -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;" \
        -c "DROP SCHEMA IF EXISTS events CASCADE;"'

echo "== restore: ładuję dane =="
gunzip -c "$FILE" | $COMPOSE exec -T postgres \
    sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q'

echo "== restore: start aplikacji =="
$COMPOSE up -d --wait

echo "== restore: healthcheck =="
./scripts/healthcheck.sh

echo "== restore: UKOŃCZONY =="
