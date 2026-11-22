#!/bin/sh
# backup.sh — nightly dump of the STAGE PostgreSQL (run on the VPS).
# Usage: scripts/backup.sh
# Output: backups/panel-stage-YYYYMMDD-HHMMSS.sql.gz, retention 7 days.
#
# Cron (runbook §5, as the `panel` user):
#   17 3 * * * /opt/panel-stage/scripts/backup.sh >> /opt/panel-stage/backups/backup.log 2>&1
#
# GDPR: rednight dumps contain whatever the stage DB holds. The standing
# assumption is SYNTHETIC test data only (deploy/stage/README.md, "Dane");
# the backup location is local to the VPS by default.
set -eu

STAGE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$STAGE_DIR"

COMPOSE="docker compose -f docker-compose.stage.yaml --env-file .env.stage"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="backups/panel-stage-${STAMP}.sql.gz"

mkdir -p backups

echo "== backup: start $(date -Is) =="

# pg_dump reads POSTGRES_USER/POSTGRES_DB from the container environment,
# so no credentials are needed here (and none are printed).
# Plain-format dump, restorable into an empty database (see restore.sh).
$COMPOSE exec -T postgres \
    sh -c 'exec pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
    | gzip -9 > "$FILE"

# Integrity check of the archive.
gzip -t "$FILE"

# Retention: keep 7 days.
find backups -type f -name 'panel-stage-*.sql.gz' -mtime +7 -delete

echo "== backup: OK $(date -Is) — $(du -h "$FILE" | cut -f1) $FILE =="
