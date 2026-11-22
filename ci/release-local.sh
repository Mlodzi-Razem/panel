#!/bin/sh
# ci/release-local.sh — MANUAL/EMERGENCY release path for STAGE, from the dev Mac.
#
# The DEFAULT release path is GitHub Actions (.github/workflows/stage.yml):
# push to `release/stage` → CI builds and pushes images to GHCR → runs
# ci/deploy-on-vps.sh on the VPS over ssh. This script is the fallback for the
# first deploy (before the GitHub repo exists), broken-CI days and offline
# work: it builds both images locally, streams them with `docker save` over ssh,
# and runs the same on-VPS switches (`up -d --wait`, healthcheck, last-stable).
#
# Usage (from the repo root):
#   ./ci/release-local.sh [options]
#     --target panel-vps          ssh alias from ~/.ssh/config (RUNBOOK §1)
#     --stage-dir /opt/panel-stage
#     --arch linux/amd64          target platform; VPS is x86_64 → amd64
#     --tag <shortsha>            override the release tag (default: git HEAD)
#     --init                      sync the deploy/stage bundle to the VPS
#                                 (idempotent, required once before first up)
#     --skip-build                reuse images already built locally
#     --auto-rollback             roll back to last-stable if the healthcheck
#                                 fails after the switch
#     --force                     allow a dirty worktree (normally refused)
#     --dry-run                   validate and print the plan, change nothing
#
# Prereqs on the Mac: docker (colima), rsync, ssh with a `panel-vps` alias.
# Cross-arch builds (--arch linux/amd64 on an arm64 Mac) additionally need
#   `brew install docker-buildx` and either colima with binfmt
#   (`docker run --privileged --rm tonistiigi/binfmt --install amd64`)
#   or a second amd64 colima profile. RUNBOOK §7 covers this in detail.
#
# Hard rules honoured: no git operations beyond read-only rev-parse; the remote
# is only touched over plain ssh as the configured user; no secrets stored.
set -eu

# --- defaults -----------------------------------------------------------------
TARGET="panel-vps"
STAGE_DIR="/opt/panel-stage"
ARCH="linux/amd64"
TAG=""
INIT="0"
SKIP_BUILD="0"
AUTO_ROLLBACK="0"
FORCE="0"
DRY_RUN="0"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --target)        TARGET="$2"; shift 2 ;;
        --stage-dir)     STAGE_DIR="$2"; shift 2 ;;
        --arch)          ARCH="$2"; shift 2 ;;
        --tag)           TAG="$2"; shift 2 ;;
        --init)          INIT="1"; shift ;;
        --skip-build)    SKIP_BUILD="1"; shift ;;
        --auto-rollback) AUTO_ROLLBACK="1"; shift ;;
        --force)         FORCE="1"; shift ;;
        --dry-run)       DRY_RUN="1"; shift ;;
        *) echo "Unknown option: $1" >&2; exit 64 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

error() { echo "release-local: $*" >&2; }

# --- 1. worktree hygiene ------------------------------------------------------
if [ -n "$(git status --porcelain)" ] && [ "$FORCE" != "1" ]; then
    error "worktree ma niezacommitowane zmiany; obraz nie byłby odtwarzalny."
    error "zacommituj (właściciel po review) albo użyj --force."
    exit 1
fi

# --- 2. release tag -----------------------------------------------------------
if [ -z "$TAG" ]; then
    TAG="$(git rev-parse --short HEAD)"
fi
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "== release-local: tag=${TAG} target=${TARGET}:${STAGE_DIR} arch=${ARCH} =="

# --- 3. compose validation (against the example env) -------------------------
STAGE_ENV_FILE=.env.stage.example \
    docker compose -f deploy/stage/docker-compose.stage.yaml \
        --env-file deploy/stage/.env.stage.example \
        config -q
echo "OK: docker compose config przechodzi."

# --- 4. remote reachable -----------------------------------------------------
if ! ssh -o BatchMode=yes "$TARGET" true 2>/dev/null; then
    error "nie mogę ssh na '${TARGET}' — sprawdź ~/.ssh/config (RUNBOOK §1)."
    exit 2
fi
echo "OK: ssh ${TARGET} działa."

# --- 5. local builds ----------------------------------------------------------
# docker reports host arch (aarch64/x86_64); CLI platforms use arm64/amd64.
NATIVE_ARCH="$(docker version --format '{{.Server.Arch}}' 2>/dev/null || echo unknown)"
case "$NATIVE_ARCH" in
    aarch64) NATIVE_ARCH="arm64" ;;
    x86_64)  NATIVE_ARCH="amd64" ;;
esac
CROSS="0"
case "${ARCH}" in
    *"${NATIVE_ARCH}") ;;
    *) CROSS="1" ;;
esac

if [ "$SKIP_BUILD" != "1" ] && [ "$DRY_RUN" != "1" ]; then
    if [ "$CROSS" = "1" ]; then
        if ! docker buildx version >/dev/null 2>&1; then
            error "budowa krzyżowa ${ARCH} na ${NATIVE_ARCH} wymaga buildx: brew install docker-buildx"
            error "oraz emulacji: docker run --privileged --rm tonistiigi/binfmt --install amd64"
            exit 3
        fi
    fi

    build() {  # name image dockefile context
        NAME="$1"; IMG="$2"; DOCKERFILE="$3"; CONTEXT="$4"
        echo "== release-local: build ${IMG} (${ARCH}) =="
        if [ "$CROSS" = "1" ]; then
            docker buildx build \
                --platform "${ARCH}" \
                --build-arg "GIT_TAG=${TAG}" \
                -t "${IMG}" \
                -f "${DOCKERFILE}" \
                --load \
                "${CONTEXT}"
        else
            docker build \
                --build-arg "GIT_TAG=${TAG}" \
                -t "${IMG}" \
                -f "${DOCKERFILE}" \
                "${CONTEXT}"
        fi
    }
    build panel-api "panel-api:${TAG}" services/panel-api/Dockerfile "$REPO_ROOT"
    build panel-web "panel-web:${TAG}" services/panel-web/Dockerfile "$REPO_ROOT/services/panel-web"
fi

# --- 6. sync the stage bundle (compose, Caddyfile, scripts, example env) -----
RSYNC_ARGS="-rlptD --exclude .env.stage --exclude release.env --exclude panel-config/ --exclude backups/ --exclude releases.txt --exclude last-stable.txt"
echo "== release-local: rsync deploy/stage + ci → ${TARGET}:${STAGE_DIR} =="
if [ "$DRY_RUN" != "1" ]; then
    ssh "$TARGET" "mkdir -p '${STAGE_DIR}' '${STAGE_DIR}/panel-config' '${STAGE_DIR}/backups'"
    # shellcheck disable=SC2086
    rsync $RSYNC_ARGS "${REPO_ROOT}/deploy/stage/" "${TARGET}:${STAGE_DIR}/"
    ssh "$TARGET" "mkdir -p '${STAGE_DIR}/ci'"
    rsync -q "$REPO_ROOT/ci/deploy-on-vps.sh" "${TARGET}:${STAGE_DIR}/ci/"
    ssh "$TARGET" "test -f '${STAGE_DIR}/.env.stage' || echo \"release-local: UWAGA: brak ${STAGE_DIR}/.env.stage — skopiuj z .env.stage.example (RUNBOOK §2)\""
fi

if [ "$INIT" = "1" ]; then
    echo ""
    echo "== release-local: --init wykonane. Następne kroki NA VPS (RUNBOOK §3/§4): =="
    echo "  ssh ${TARGET}"
    echo "  cd ${STAGE_DIR}"
    echo "  cp .env.stage.example .env.stage   # i uzupełnij sekrety (RUNBOOK §2)"
    echo "  make up                            # pierwszy start (migracje zrobi panel-api)"
    echo "  make health"
    echo "  crontab -e                         # linia backupu (RUNBOOK §5)"
    echo "  (dalsze wydania: ./ci/release-local.sh z Maca albo GitHub Actions)"
    exit 0
fi

if [ "$DRY_RUN" = "1" ]; then
    echo "== release-local: DRY-RUN — plan wygląda OK, nic więcej nie zrobiłem =="
    exit 0
fi

# --- 7. stream images to the VPS ---------------------------------------------
echo "== release-local: docker save | ssh docker load =="
docker save "panel-api:${TAG}" "panel-web:${TAG}" \
    | gzip \
    | ssh "$TARGET" "gzip -d | docker load"

# --- 8. switch the stack ------------------------------------------------------
echo "== release-local: przełączenie na ${TAG} =="
SSH_STATUS=0
ssh "$TARGET" "cd '${STAGE_DIR}' && \
    docker tag panel-api:${TAG} panel-api:stage && \
    docker tag panel-web:${TAG} panel-web:stage && \
    ./ci/deploy-on-vps.sh --api-image panel-api:${TAG} --web-image panel-web:${TAG} --skip-pull" \
    || SSH_STATUS=$?

# --- 9. failure handling -------------------------------------------------------
if [ "$SSH_STATUS" != "0" ]; then
    if [ "$AUTO_ROLLBACK" = "1" ]; then
        echo "== release-local: healthcheck padł — auto rollback do last-stable =="
        ssh "$TARGET" "cd '${STAGE_DIR}' && ./scripts/rollback.sh --yes" || true
        exit 9
    fi
    error "healthcheck po wdrożeniu nie przeszedł (kod ${SSH_STATUS})."
    error "na VPS: cd ${STAGE_DIR} && ./scripts/rollback.sh"
    exit 9
fi

echo "== release-local: SUKCES — ${TAG} na ${TARGET} =="
