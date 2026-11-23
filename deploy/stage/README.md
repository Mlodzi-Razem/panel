# deploy/stage — środowisko STAGE Panelu MR

Stage = środowisko testowe właściciela na jego VPS Ubuntu x86_64:
**`https://dev.panel.mlodzirazem.org`**, baza PostgreSQL 18.1, `panel-api`
(Spring Boot, Java 25, migracje Flyway przy starcie) i `panel-web` (Next.js
standalone). Dwa tory utrzymywania (decyzja właściciela, raport „Sekcja K"):

- **tor C (domyślny)** — docker compose (Caddy + ACME): ten katalog główny
  (`docker-compose.stage.yaml` itd.); porty 3000/8080/9080 wyłącznie w
  sieci wewnętrznej,
- **tor K** — microk8s AD-9 z chartem `cloud/`: [microk8s/](microk8s/)
  (overlay + szablon sekretów) + `ci/deploy-helm-on-vps.sh`; RUNBOOK §11.

**Runbook właściciela (kompletny, krok po kroku): [RUNBOOK.md](RUNBOOK.md).**

## Ścieżka release'u (jedna domyślna)

```text
commit (właściciel, po review; GPG)
   │ push na branch release/stage (albo tag v*)
   ▼
GitHub Actions (.github/workflows/stage.yml)
   ├─ panel-api: mvn verify (Testcontainers na runnerze) → artefakt panel-api-jar
   ├─ build obrazów linux/amd64 → GHCR (tag sha-<hash> + branch + "stage");
   │    panel-api z tego jara (Dockerfile.prebuilt, bez Mavena; RUNBOOK §6.6)
   └─ ssh na VPS: rsync deploy/stage → ci/deploy-on-vps.sh
        ├─ docker compose pull panel-api, panel-web
        ├─ docker compose up -d --wait
        ├─ scripts/healthcheck.sh
        └─ healthcheck OK → last-stable.txt (= rollback pointer)
                          → releases.txt (historia)

rollback: scripts/rollback.sh (VPS) albo workflow_dispatch z rollback_to;
          cel = last-stable.txt albo jawny tag sha-<hash>
```

Ścieżka **ręczna/awaryjna** (pierwszy deploy przed skonfigurowaniem GitHuba,
CI leży): `ci/release-local.sh` z Maca — buildx (amd64) → `docker save | ssh
docker load` → ten sam `deploy-on-vps.sh` → healthcheck. Appendix bez
GitHuba: `ci/post-receive.example` (bare repo na VPS).

## Pliki

| plik | rola |
|---|---|
| `docker-compose.stage.yaml` | tor C: postgres / panel-api / panel-web / caddy; sieci apps (internal) + edge |
| `Caddyfile` | tor C: reverse proxy + TLS (STAGE_HOST, ACME via 80/443) |
| `.env.stage.example` | tor C: szablon env (publiczne domyślne; sekrety wkłada właściciel na VPS) |
| `Makefile` | up/down/logs/psql/health/backup/restore/rollback/config/release |
| `scripts/healthcheck.sh` | tor C: end-to-end healthcheck (TLS, /api/health, actuator, restarty) |
| `scripts/backup.sh` | tor C: pg_dump gz + retencja 7 dni (linia cron w RUNBOOK §5) |
| `scripts/restore.sh` | tor C: odtworzenie bazy z backupu (destrukcyjne, z potwierdzeniem) |
| `scripts/rollback.sh` | tor C: powrót do last-stable / jawnego tagu; `--list` |
| `microk8s/` | tor K: overlay + szablon sekretów + README (RUNBOOK §11) |
| `RUNBOOK.md` | procedury właściciela: init VPS, env, start, cron, GitHub, SSO, GDPR, tor K |
| `ci/…` (repo root) | `deploy-on-vps.sh` (tor C), `deploy-helm-on-vps.sh` (tor K), `release-local.sh` (ścieżka ręczna), `post-receive.example` (appendix) |

Dockerfilee obrazów: [services/panel-api/Dockerfile](../../services/panel-api/Dockerfile)
i [services/panel-web/Dockerfile](../../services/panel-web/Dockerfile)
(multi-stage; maven → JRE 25, node 24 LTS → node:24-alpine standalone).
CI buduje panel-api z jara po verify:
[services/panel-api/Dockerfile.prebuilt](../../services/panel-api/Dockerfile.prebuilt)
(ten sam etap runtime; RUNBOOK §6.6).

## Architektura / decyzje

- **Postgres = 18.1-alpine**: migracja `V0.0.1.03` używa kolumn VIRTUAL
  generated (wymagają PG18); migracji wykonuje sam `panel-api` (Flyway) —
  ręczne kroki bazodanowe nie istnieją.
- **Caddy** publikuje wyłącznie 80/443 (+443/udp HTTP/3); CA = Let's Encrypt
  (produkcja; testowa: `ACME_CA` w env).
- **Sieć**: `apps` jest `internal: true` (postgres/api/web nie mają
  internetu); `edge` — caddy + web. panel-web woła panel-api server-side
  (`http://panel-api:8080`), przeglądarka nigdy nie pada na API bezpośrednio.
- **Współistnienie z microk8s**: na VPS działa microk8s (AD-9); stage to
  osobny compose obok klastra. Stack publikuje wyłącznie 80/443
  (+443/udp), nie dotyka portów microk8s (16443, 19001, 10248, 10256,
  9100, 7472/7946) ani jego stanu — świadomie wybieramy izolację zamiast
  instalacji chartu `cloud/` (decyzja architektoniczna: patrz raport).
- **Obrazy**: GHCR (private) — `ghcr.io/<owner>/panel-{api,web}:sha-<hash>`;
  mutable `:stage` = podgląd ostatniego wydania. W ścieżce ręcznej te same
  serwisy jako obrazy lokalne `panel-{api,web}:stage`.
- **SSO/Entra: domyślnie WYŁĄCZONE** (puste `PANEL_ENTRA_*`); włączenie —
  RUNBOOK §9 (redirect URI w Entra + certyfikaty w `panel-config/`, nigdy
  przez czat).

## Dane (GDPR)

Stage **nie dostaje** dev-seedów osób (`dev/seed/test-members.sql` — wyłącznie
dev). Baza startuje z migracji (schemat + syntetyczne szkielety organizacyjne).
Zalecenie stałe: na stage wyłącznie dane syntetyczne; backupy (`backups/`,
retencja 7 dni) noszą dane członków tylko jeśli kiedyś włożysz tam dane
prawdziwe — i wtedy traktuj je jak RODO art. 9 (RUNBOOK §10).
