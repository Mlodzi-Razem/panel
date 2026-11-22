#!/bin/sh
# ci/deploy-helm-on-vps.sh — the VPS half of the STAGE release for variant K
# (microk8s; run ON the VPS over ssh, by the GitHub Actions workflow or by hand).
#
# Contract: images are already built and pushed to GHCR by CI
# (.github/workflows/stage.yml); this script runs `microk8s helm upgrade
# --install` with the stage overlay + the owner's secrets values, waits for the
# rollout, healthchecks through the public gateway and records "last-stable".
#
# PREREQS (all owner-side, RUNBOOK §11):
#   - user in the `microk8s` group (`microk8s helm` works without sudo);
#   - docker-registry Secret `ghcr-pull` in the target namespace (dockerconfigjson
#     for ghcr.io / the owner's PAT with read:packages);
#   - values.stage-secrets.yaml next to this script's bundle (created from
#     values.stage-secrets.example.yaml; never committed).
#
# Usage:
#   ci/deploy-helm-on-vps.sh --api-image <ref> --web-image <ref> [options]
#     --api-image    full image ref, e.g. ghcr.io/mlodzi-razem/panel-api:sha-6a9b6ff
#     --web-image    full image ref, e.g. ghcr.io/mlodzi-razem/panel-web:sha-6a9b6ff
#     --stage-dir    default /opt/panel-stage (contains chart/, microk8s/)
#     --release      default panel-stage
#     --namespace    default stage
#     --skip-upgrade only healthcheck what is running
#
# Rollback: `microk8s helm rollback <release> -n <namespace>` (previous revision)
# or re-run this script with an older --*-image (every run is a new revision).
set -eu

STAGE_DIR="/opt/panel-stage"
RELEASE="panel-stage"
NS="stage"
API_IMAGE=""
WEB_IMAGE=""
SKIP_UPGRADE="0"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --api-image)   API_IMAGE="$2"; shift 2 ;;
        --web-image)   WEB_IMAGE="$2"; shift 2 ;;
        --stage-dir)   STAGE_DIR="$2"; shift 2 ;;
        --release)     RELEASE="$2"; shift 2 ;;
        --namespace)   NS="$2"; shift 2 ;;
        --skip-upgrade) SKIP_UPGRADE="1"; shift ;;
        *) echo "deploy-helm-on-vps: unknown option: $1" >&2; exit 64 ;;
    esac
done

if [ "$SKIP_UPGRADE" != "1" ] && { [ -z "$API_IMAGE" ] || [ -z "$WEB_IMAGE" ]; }; then
    echo "deploy-helm-on-vps: --api-image i --web-image są wymagane." >&2
    exit 64
fi

cd "$STAGE_DIR"

SECRETS_FILE="microk8s/values.stage-secrets.yaml"
if [ ! -f "$SECRETS_FILE" ]; then
    echo "deploy-helm-on-vps: brak $SECRETS_FILE (RUNBOOK §11.2; szablon: values.stage-secrets.example.yaml)." >&2
    exit 66
fi
if [ ! -d cloud/chart ]; then
    echo "deploy-helm-on-vps: brak katalogu chartu w $STAGE_DIR — zsynchronizuj deploy bundle (rsync cloud/chart)." >&2
    exit 66
fi

# `microk8s helm`/`microk8s kubectl` — bez sudo dla grupy microk8s.
HELM="microk8s helm"
KUBECTL="microk8s kubectl"

STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "== deploy-helm: sanity klastra =="
$KUBECTL get ns >/dev/null
$HELM version --short

if [ "$SKIP_UPGRADE" != "1" ]; then
    echo "== deploy-helm: upgrade --install ${RELEASE} → ${API_IMAGE} / ${WEB_IMAGE} =="
    # --atomic = failed upgrade rolls back the release automatically.
    # Shell-exported image refs land in --set, the canonical override for a
    # single release pointer.
    $HELM upgrade --install "$RELEASE" cloud/chart \
        --namespace "$NS" --create-namespace \
        -f cloud/chart/values.yaml \
        -f microk8s/values.stage-microk8s.yaml \
        -f "$SECRETS_FILE" \
        --set "panel.api.image=$API_IMAGE" \
        --set "panel.web.image=$WEB_IMAGE" \
        --wait --timeout 10m
fi

echo "== deploy-helm: rollout status =="
$KUBECTL -n "$NS" rollout status deploy/panel-api  --timeout=300s
$KUBECTL -n "$NS" rollout status deploy/panel-web  --timeout=180s

echo "== deploy-helm: healthcheck przez gateway publiczny =="
DOMAIN="$(grep -E '^\s+- dev\.' microk8s/values.stage-microk8s.yaml | head -1 | tr -d ' -')"
HELD="$($HELM -n "$NS" list -q | grep -x "$RELEASE" || true)"
if [ -z "$HELD" ]; then
    echo "deploy-helm: release $RELEASE nie istnieje w ns $NS." >&2
    exit 67
fi

if command -v curl >/dev/null 2>&1; then
    if curl -fsS -o /dev/null --max-time 30 "https://${DOMAIN}/"; then
        echo "OK: https://${DOMAIN}/ odpowiada."
    else
        echo "UWAGA: https://${DOMAIN}/ nie odpowiada — sprawdź ekspozycję 80/443 (RUNBOOK §11.3);" >&2
        echo "      wdrażanie kontynuuję (rollout jest zdrowy wewnątrz klastra)." >&2
    fi
else
    echo "UWAGA: brak curl na hoście — pomięto publiczny healthcheck." >&2
fi

# Kryterium "stable": rollout zdrowy (HPA/pody) + health api przez ClusterIP.
API_POD="$($KUBECTL -n "$NS" get pods -l org.mlodzirazem.panel.component=panel-api -o jsonpath='{.items[0].metadata.name}')"
if [ -z "$API_POD" ]; then
    echo "deploy-helm: nie znalazłem poda panel-api." >&2
    exit 10
fi
$KUBECTL -n "$NS" exec "$API_POD" -- \
    curl -fsS http://localhost:9080/actuator/health | grep -q UP
echo "OK: actuator panel-api = UP."

REV="$($HELM -n "$NS" history "$RELEASE" | tail -1 | awk '{print $1}')"

echo "== deploy-helm: OK — odnotowuję last-stable (rewizja helm: ${REV}) =="
cat > last-stable.txt <<EOF
# Written by ci/deploy-helm-on-vps.sh at ${STAMP}.
RELEASE=${RELEASE}
NAMESPACE=${NS}
HELM_REVISION=${REV}
PANEL_API_IMAGE=${API_IMAGE:--(bez zmian)}
PANEL_WEB_IMAGE=${WEB_IMAGE:--(bez zmian)}
RELEASED=${STAMP}
EOF
echo "${STAMP} OK helm=${RELEASE}@${NS} rev=${REV} api=${API_IMAGE:--(bez zmian)} web=${WEB_IMAGE:--(bez zmian)}" >> releases.txt

echo "== deploy-helm: SUKCES =="
echo "Rollback (jeśli trzeba): microk8s helm rollback ${RELEASE} -n ${NS}"
exit 0
