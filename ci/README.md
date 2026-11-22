# ci/ — mechanizmy release'u STAGE

Poniżej rola plików; decyzje projektowe i diagram przepływu są w
[deploy/stage/README.md](../deploy/stage/README.md) i w raporcie
(`/tmp/stage-deploy/report.md` w sesji warsztatu).

| plik | ścieżka | status |
|---|---|---|
| `../.github/workflows/stage.yml` | GitHub Actions (verify → build+push GHCR → deploy → health) | **domyślna** |
| `deploy-on-vps.sh` | tor C (compose): pull + `up -d --wait` + healthcheck + `last-stable` | domyślny deploy (env `DEPLOY_VARIANT: compose`) |
| `deploy-helm-on-vps.sh` | tor K (microk8s): `microk8s helm upgrade --install` + rollout + healthcheck + `last-stable`; rollback = `helm rollback` | wariant K (env `DEPLOY_VARIANT: helm`; RUNBOOK §11) |
| `release-local.sh` | ręcznie z Maca: buildx → `docker save \| ssh docker load` → deploy-on-vps | awaryjna / pierwszy deploy przed GitHubem |
| `post-receive.example` | bare repo na VPS (`git init --bare`) + hook checkout → rsync (+ opcjonalnie build na VPS) | **appendix** — opcja „bez GitHuba" |

Oba tory deploy mają wspólny build (Dockerfile'e, GHCR, verify). Przełączenie
całego pipeline: jedna linia `DEPLOY_VARIANT` w nagłówku workflow (po decyzji
właściciela; kroki toru K: `../deploy/stage/RUNBOOK.md §11`).

## Konwencja branchy

- `main` — kod bieżący; PR-y na `main`/`release/stage` uruchamiają job verify panel-api.
- `release/stage` — **wskaźnik wydania na stage**: push = deploy na VPS.
- `v*` — wydania wersjonowane (ten sam pipeline; tag ląduje na GHCR).

## Secrets wymagane przez workflow (bez wartości tutaj!)

Ustawia właściciel w GitHubie (Settings → Secrets → Actions): `SSH_PRIVATE_KEY`
(klucz deploy-only), `VPS_HOST`, `VPS_USER`, `VPS_KNOWN_HOSTS`. Publikację na GHCR
załatwia `GITHUB_TOKEN` (packages:write); pull na VPS — `docker login ghcr.io`
z PAT (read:packages) właściciela, po prostu na serwerze. Kompletny opis:
[deploy/stage/RUNBOOK.md §6](../deploy/stage/RUNBOOK.md).

## Appendix: post-receive (bez GitHuba)

Instalacja i ograniczenia — w nagłówku `post-receive.example`. Appendix: bez
GitHuba tracisz (a) runner verify, (b) cache obrazów w rejestrze, (c) reruny
workflow; hook buduje obrazy NA VPS tylko jeśli masz wolne ~4 GB RAM (Maven).
Dlatego to appendix, a nie ścieżka domyślna.
