# RUNBOOK — środowisko STAGE Panelu MR na VPS właściciela

Audience: **właściciel**. Wszystkie komendy są do wklejenia; nic tu nie wymaga
AI ani asystenta na serwerze. Zasady niezmienne: żadne sekrety nie przechodzą
przez czat/pliki repo; asystent nigdy nie loguje się na VPS i nie wpisuje
żadnych wartości — wszystko poniżej robisz Ty.

Środowisko docelowe (ustalone): Ubuntu 24.04 x86_64, użytkownik **nepose**,
host publiczny **`dev.panel.mlodzirazem.org`** (rekord A → IP VPS), stage dir
**`/opt/panel-stage`**. Ścieżka domyślna release'u: **GitHub Actions → GHCR →
ssh → VPS**; ścieżka awaryjna/ręczna: `ci/release-local.sh` z Maca (§3 i §7).

**Dwa tory utrzymywania STAGE (decyzja architektoniczna jest po Twojej stronie,
raport: „Sekcja K"):**
- **Tor C (compose)** — docker compose obok microk8s. Domyślny w tym runbooku
  (§1–§10), najmniej ryzykowny; klaster microk8s zostaje nietknięty,
  workflow ma `DEPLOY_VARIANT: compose`.
- **Tor K (microk8s, prod-parity AD-9)** — ten sam chart `cloud/`, altanka w
  klastrze, który jest już przygotowany (istio, cert-manager, CNPG, KPS,
  MetalLB — inwentarz w §11). Kroki: §11; workflow przełączasz jedną linią
  (`DEPLOY_VARIANT: helm`). Obrazy z GHCR, workflow i Dockerfile'y są wspólne
  dla obu torów; różni się tylko warstwa uruchomieniowa.

> Numeracja odzwierciedla kolejność pierwszego uruchomienia. Kroki §1–§5
> zrealizujesz raz; potem codziennością jest §7 (release) i §8 (rollback).

---

## §1. Init VPS (dopasowany do ISTNIEJĄCEJ maszyny Ubuntu; patrz „Fakty z VPS" niżej)

Założenie zweryfikowane: Ubuntu 24.04.5 LTS, 8 vCPU, 22 GiB RAM, 193 GB dysku,
user **nepose** istnieje, **Docker 29.10 + Compose v5.8 już zainstalowany
(usługa działa)**, porty 80/443 wolne, a obok stage będzie działał **microk8s**
(snap; jego portów nie ruszamy — lista niżej). Instalację Dockera od zera
trzymam w Appendixie A (na wypadek innej maszyny).

**1.1. User `nepose` w grupie `docker`** (jedyna naprawa przy uprawnieniach Dockera;
bez tego `docker.sock` odmawia dostępu):

```bash
groups nepose                                  # przed: "nepose sudo"
sudo usermod -aG docker nepose
exit                                           # i ZALOGUJ SIĘ PONOWNIE
groups                                         # po re-logu musi zawierać "docker"
docker run --rm hello-world && docker compose version   # test bez sudo
```

Dla toru **K (microk8s, §11)** grupa `docker` nie jest potrzebna — zamiast niej:

```bash
sudo usermod -aG microk8s nepose                # daje `microk8s kubectl`/`microk8s helm` bez sudo
exit                                           # i re-login
microk8s kubectl get ns                        # test
```

(Klaster microk8s działa na tej maszynie od początku — patrz „Fakty z VPS" —
i zakłada grupę `microk8s` automatycznie. Obie grupy można mieć naraz.)

**1.2. Pakiety pomocnicze + firewall (Do ZWERYFIKOWANIA):** sprawdź, czy ufw
w ogóle jest aktywny — poniższa konfiguracja jest wzorcem dopiero gdy
ustalisz stan:

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl rsync ufw git
sudo ufw status verbose
```

- **Stan `Status: inactive`** → decyzja Twoja (patrz też „Fakty z VPS":
  obserwacje bezpieczeństwa — publiczne 16443 i 9100). Jeśli włączasz:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp     # ssh
sudo ufw allow 80/tcp     # http (ACME HTTP-01, przekierowania)
sudo ufw allow 443/tcp    # https (TLS-ALPN-01 + ruch)
sudo ufw allow 443/udp    # HTTP/3 (QUIC)
# UWAGA przed `enable`: domyślny deny incoming odetnie publiczne dziś porty
# microk8s (16443, 9100, 7472/7946). Jeśli korzystasz z nich ZDALNIE,
# dodaj najpierw odpowiednio, np.:
#   sudo ufw allow 16443/tcp   # kube-apiserver (microk8s) — tylko w razie potrzeby
#   sudo ufw allow from <TWOJA-STAŁA-IP> to any port 16443 proto tcp   # wersja restrykcyjna
sudo ufw enable
sudo ufw status verbose
```

- Stage (Caddy) potrzebuje wyłącznie 22/80/443(+443/udp) — compose nie publikuje
  nic więcej, więc reszta metod portów jest niebrudzona przez to wdrożenie.

**1.3. Minimalne sshd hardening** (ostrożnie — nie zablokujesz siebie; przetestuj
w drugim oknie przed wylogowaniem):

```bash
sudo tee /etc/ssh/sshd_config.d/10-panel-stage.conf >/dev/null <<'EOF'
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
X11Forwarding no
EOF
sudo sshd -t && sudo systemctl reload ssh
```

**1.4. Alias ssh na Macu dev** (skrypty i workflow używają `panel-vps`). W
`~/.ssh/config` na Macu:

```
Host panel-vps
    HostName dev.panel.mlodzirazem.org     # albo IP z rekordu A
    User nepose
    Port 22
    IdentityFile ~/.ssh/id_ed25519
```

Test: `ssh panel-vps 'docker --version && free -h'`.

**Fakty z VPS (stan znany na czas pisania):**

| obszar | stan | konsekwencja |
|---|---|---|
| Docker/Compose | 29.10.0 / v5.8.0, usługa działa | brak instalacji; tylko grupa (1.1) |
| `nepose` | bez grupy docker | naprawa w 1.1 obowiązkowa |
| ufw | do zwerfikowania (1.2) | decyzja właściciela o regułach |
| porty 80/443 | wolne | Caddy bindować może |
| microk8s | działa (k8s 1.35) | NIE dotykamy; stage = compose obok k8s (izolacja) |
| porty microk8s | 16443, 19001, 10248, 10256, 9100, 7472/7946 | compose ich nie używa |
| crontab nepose | pusty | linia backupu wejdzie bez konfliktów (§5) |
| RAM/dysk | 22 GiB / 193 GB | build na VPS wykonalny; domyślnie jednak GH Actions |
| rekord A | dev.panel.mlodzirazem.org → VPS | ACME dostępne od razu |

---

## §2. Plik środowiska `.env.stage`

Kopiujesz na VPS przy pierwszym transporcie (§3); potem TY uzupełniasz wartości.

**2.1. Wygeneruj sekrety** (każdy inny; nigdy ich nie wklejaj do czatu/repo;
trzymaj w `.env.stage` i w swoim menedżerze haseł):

```bash
cd /opt/panel-stage
cp .env.stage.example .env.stage
chmod 600 .env.stage
openssl rand -base64 24   # → POSTGRES_PASSWORD (raz! wklej też jako JDBC_PASSWORD — patrz niżej)
openssl rand -base64 48   # → PANEL_API_AUTH_SECRET (wspólny dla panel-api i panel-web)
openssl rand -base64 32   # → NEXTAUTH_SECRET
openssl rand -base64 24   # → PANEL_METRICS_AUTH_TOKEN
```

**2.2. Wypełnij w `.env.stage`** (edytor: `nano .env.stage`):

| zmienna | co wstawić |
|---|---|
| `STAGE_HOST` | `dev.panel.mlodzirazem.org` (już jest) |
| `ACME_EMAIL` | Twój e-mail powiadomień CA |
| `POSTGRES_PASSWORD` | wartość wygenerowana (jedna i ta sama co `JDBC_PASSWORD`… patrz PS niżej) |
| `PANEL_API_AUTH_SECRET` | wartość wygenerowana (≥32 bajty) |
| `NEXTAUTH_SECRET` | wartość wygenerowana |
| `PANEL_METRICS_AUTH_TOKEN` | wartość wygenerowana |
| `NEXTAUTH_URL` | `https://dev.panel.mlodzirazem.org` (już jest) |
| `PANEL_ENTRA_*` | **na start puste** (SSO wyłączone; §9) |

PS: w tym szablonie `JDBC_PASSWORD` czyta panel-api z podstawienia compose
(ten sam `POSTGRES_PASSWORD`) — w pliku env zmiennej `JDBC_PASSWORD` nie
wypełniasz; wystarczy `POSTGRES_PASSWORD`.

**2.3. Walidacja składni:**

```bash
make config
# → "OK: compose config przechodzi (.env.stage)."
```

---

## §3. Pierwszy transport i start compose (ścieżka ręczna z Maca)

Przed pierwszym GitHub Actions (repo zdalne jeszcze nie istnieje) użyj
ścieżki awaryjnej. **Na Macu**, w worktree `/Users/nepose/Projects/panel/panel-stage`:

```bash
./ci/release-local.sh --init          # rsync bundle'u deploy/stage + ci → VPS (idempotentne)
ssh panel-vps 'cd /opt/panel-stage && cp .env.stage.example .env.stage && chmod 600 .env.stage'
ssh panel-vps 'cd /opt/panel-stage && nano .env.stage'   # uzupełnij sekrety jak w §2
./ci/release-local.sh                  # ostatni commit HEAD: build 2 obrazów (amd64) → save|ssh load → up → healthcheck
```

Uwaga: Mac jest arm64, VPS amd64 — pełny build krzyżowy wymaga jednorazowo:

```bash
brew install docker-buildx
docker run --privileged --rm tonistiigi/binfmt --install amd64
```

(albo drugi profil colima architektury x86_64: `colima start --arch x86_64
--profile amd`; potem `docker --context colima-amd …` — wariant dla
cierpliwych; binfmt zwykle wystarcza). Stan obrazów na VPS:

```bash
ssh panel-vps 'docker images | grep -E "panel-(api|web)"'
```

Alternatywa bez budowania na Macu: poczekać ze startem na §6 (GitHub Actions
zbuduje na runnerze amd64) — ale wtedy §2 wykonaj dopiero po skonfigurowaniu
repo, bo pierwsze uruchomienie `make up` musi mieć `.env.stage` gotowy.

---

## §4. Healthcheck end-to-end

Po starcie (§3 albo §6) sprawdź kompletność środowiska:

```bash
ssh panel-vps 'cd /opt/panel-stage && make health'
```

Oczekiwany wynik: 4× OK (web przez Caddy z TLS, `/api/health`, actuator = UP,
brak kontenerów w restarting). W przeglądarce: `https://dev.panel.mlodzirazem.org`
— strona startowa się załaduje; ekran logowania jest dostępny, ale logowanie
Google/Entra **celowo nie działa** (placeholdery w env, SSO wyłączone — §9).

Ręcznie (bez make):

```bash
curl -fsS https://dev.panel.mlodzirazem.org/api/health           # → {"status":"ok"}
ssh panel-vps 'docker compose -f /opt/panel-stage/docker-compose.stage.yaml \
    --env-file /opt/panel-stage/.env.stage \
    exec panel-api curl -fsS http://localhost:9080/actuator/health'   # → {"status":"UP",...}
```

Pierwszy start trwa dłużej: panel-api wykonuje **migracje Flyway** (PostgreSQL
musi być ≥18.1 — obraz `postgres:18.1-alpine` to gwarantuje; nic nie robisz
ręcznie z bazą).

---

## §5. Cron backupu

**Na VPS**, jako `nepose`:

```bash
crontab -e
```

i wklej linię:

```cron
17 3 * * * /opt/panel-stage/scripts/backup.sh >> /opt/panel-stage/backups/backup.log 2>&1
```

Sprawdzenie od razu:

```bash
ssh panel-vps 'cd /opt/panel-stage && ./scripts/backup.sh && ls -lh backups/ | tail -3'
```

Backup: `pg_dump | gzip` do `backups/panel-stage-YYYYMMDD-HHMMSS.sql.gz`,
retencja **7 dni** (starsze kasowane przez skrypt). Przywracanie:

```bash
ssh panel-vps 'cd /opt/panel-stage && make restore FILE=backups/panel-stage-20251122-031701.sql.gz'
```

Destrukcyjne (drop schematów → load → up → healthcheck) — używać świadomie;
ćwiczenie warto raz zrobić na stage (patrz też §10 o danych).

---

## §6. Ścieżka domyślna: GitHub Actions (konfiguracja jednorazowa)

Celem jest: **push na branch `release/stage` = deploy na stage** (jawny,
przewidywalny); osobno tagi `v*` robią to samo (wersjonowane wydanie).
Workflow: `.github/workflows/stage.yml` (verify panel-api → build 2 obrazów
linux/amd64 → GHCR → ssh deploy → healthcheck → `last-stable`). Obraz
panel-api powstaje z jara, który przeszedł verify — szczegóły w §6.6.

**6.1. Repozytorium GitHub** — istnieje już jako **private**:
`github.com/Mlodzi-Razem/panel` (remote `origin` jest w configu; wypchnięte
gałęzie: `main`, `feat/members`, `feat/workspace-domain-signin`). Po review i
Twoim commicie w worktree wypychasz:

```bash
git push origin <branch>                # NIGDY bez Twojej zgody — to Twój krok
git push origin <branch>:release/stage  # pierwszy deploy na stage
```

(Do GitHuba klucz ssh/HTTPS — Twoja standardowa autoryzacja; pull-request dla
`release/stage` też działa jako trigger weryfikacji `panel-api`.)

**6.2. Klucz deploy-only dla CI.** Na Macu:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/panel_vps_ci_ed25519 -N "" -C "panel-github-actions-deploy"
cat ~/.ssh/panel_vps_ci_ed25519.pub
# ↑ publiczny dopisz na VPS:
ssh panel-vps 'echo "<KLUCZ-PUBLICZNY-CI>" >> ~/.ssh/authorized_keys'
```

(Opcjonalne ograniczenie tego klucza tylko do deployu — na VPS w
`~/.ssh/authorized_keys` przed kluczem dodaj
`restrict,command="/home/nepose/deploy-command.sh"` tylko jeśli wdrożysz wrapper;
bez tego klucz działa jak zwykły login — do decyzji właściciela.)

**6.3. Repo secrets** (GitHub → *Repo* → Settings → Secrets and variables →
Actions → *New repository secret*). Lista dokładna:

| secret | wartość (wklejasz Ty) |
|---|---|
| `SSH_PRIVATE_KEY` | zawartość `~/.ssh/panel_vps_ci_ed25519` (prywatny klucz CI) |
| `VPS_HOST` | `<IP_VPS>` (albo DNS `dev.panel.mlodzirazem.org`, jeśli stabilne) |
| `VPS_USER` | `nepose` |
| `VPS_KNOWN_HOSTS` | wynik: `ssh-keyscan -H <IP_VPS> 2>/dev/null` (jedna linia) |

**6.4. Pull z GHCR na VPS** (repo private ⇒ obrazy private). Na VPS, jeden raz:

```bash
# na github.com: Settings → Developer settings → Personal access token →
# classic PAT z zakresem read:packages  (albo fine-grained z dostępem do pakietu)
ssh panel-vps 'echo <PAT-READ-PACKAGES> | docker login ghcr.io -u <LOGIN-GITHUB> --password-stdin'
```

(PAT trafia do `~/.docker/config.json` na VPS — nigdy do repo/workflow.)

**6.5. Odpalenie:** `git push github release/stage` (albo PR + merge → push).
Obserwuj zakładkę *Actions*: **verify → build+push → deploy**; deploy kończy
się healthcheckiem i dopisem do `last-stable.txt` na VPS.

**6.6. Jak powstaje obraz panel-api (od 2026-11-23).** Maven działa w CI
**raz**: w jobie „panel-api verify". Job obrazów nie pobiera już zależności.

| krok | job | co się dzieje |
|---|---|---|
| 1 | panel-api verify | `mvn verify` (cache `setup-java`); retry resolvera na 429/503: `-Daether.connector.http.retryHandler.count=6`, `…interval=10000` |
| 2 | panel-api verify | tylko przy push: `sha256sum` jara + artefakt `panel-api-jar` (retencja 3 dni, `overwrite: true` na re-run) |
| 3 | Build and push images (panel-api) | `download-artifact` (digest sprawdzany, niezgodność = błąd) → `services/panel-api/target/` → `sha256sum` (ma się zgadzać z krokiem 2) |
| 4 | Build and push images (panel-api) | `docker build -f services/panel-api/Dockerfile.prebuilt services/panel-api`: sam rozkład jara na warstwy + runtime |

- **Dlaczego:** wcześniej `services/panel-api/Dockerfile` budował jar drugi raz
  w `docker build`. Warstwa `dependency:go-offline` nie pokrywała wszystkiego,
  więc build obrazu pobierał zależności z Maven Central z IP współdzielonych
  runnerów. Release `33b3783` padł na `429 Too Many Requests`.
- **Gwarancja:** do obrazu trafia bajt w bajt ten jar, który przeszedł testy.
- **Etap runtime** (JRE 25 jammy, `USER 10001:10001`, warstwy
  `jarmode=tools`, `launch.sh`/`healthcheck.sh`, porty 8080/9080,
  `HEALTHCHECK`) jest identyczny w obu Dockerfile'ach. Krok verify „Runtime
  stages of both panel-api Dockerfiles are identical" robi `diff` od znacznika
  `# 2. Runtime` i przerywa pipeline przy rozjeździe. Zmieniasz runtime → zmień
  oba pliki.
- **Re-run:** „Re-run failed jobs" na samym buildzie obrazów pobiera artefakt
  z udanego verify tego samego runu (retencja 3 dni). Gdy krok „Fetch the jar
  that passed verify" zgłosi brak artefaktu (np. po 3 dniach) — „Re-run all
  jobs".
- **Build lokalny** (Mac, legacy builder bez buildx) — dwie drogi, oba pliki
  są bez składni BuildKit:

```bash
# a) z gotowego jara (to samo co CI); jar recepturą Docker-Maven, z katalogu repo:
docker run --rm -v "$PWD":/workspace -w /workspace -v maven-home-panel:/m2 \
  --user 501:20 --group-add 991 -e MAVEN_OPTS=-Xmx512m maven:3.9-eclipse-temurin-25 \
  mvn -B -Dmaven.repo.local=/m2/repository -pl services/panel-api -am package -DskipTests
docker build -f services/panel-api/Dockerfile.prebuilt \
  -t panel-api:$(git rev-parse --short HEAD) \
  --build-arg GIT_TAG=$(git rev-parse --short HEAD) services/panel-api

# b) od źródeł, wszystko w docker build (Makefile `deploy/stage`, ci/release-local.sh):
docker build -f services/panel-api/Dockerfile \
  -t panel-api:$(git rev-parse --short HEAD) \
  --build-arg GIT_TAG=$(git rev-parse --short HEAD) .
```

Kontekst w (a) to `services/panel-api`, nie root repo: root `.dockerignore`
wycina `**/target`. `services/panel-api/.dockerignore` zawęża kontekst do
`target/panel-api-*.jar` i dwóch skryptów z `docker/`. W `target/` musi być
**dokładnie jeden** `panel-api-*.jar`, inaczej build przerywa się z komunikatem.

---

## §7. Procedura release'u — codzienna

**Domyślnie:**

```bash
# Mac: po review i Twoim commicie
git push github release/stage
```

Gotowe. Sprawdź Actions + po chwili `ssh panel-vps 'cd /opt/panel-stage && make health'`.

**Ręcznie/awaryjnie** (CI leży, offline, chcesz wymusić starszy commit):

```bash
./ci/release-local.sh                       # HEAD: full pipeline z Maca
./ci/release-local.sh --tag 6a9b6ff         # konkretny commit
./ci/release-local.sh --auto-rollback       # jak wyżej; fail → auto rollback
./ci/release-local.sh --dry-run             # sama walidacja planu
```

Parametry: `--arch linux/amd64` (default; VPS=x86_64), `--target panel-vps`,
`--stage-dir /opt/panel-stage`, `--init` (sync plików), `--force` (brudny
worktree — odradzane). Skrypt odmawia przy niezacommitowanych zmianach (obraz
musi być odtwarzalny).

**Konwencja branchy (rekomendacja):** `main` = kod aktualny (bez deployu);
`release/stage` = wskaźnik wydania na stage (push = deploy); `v*` = pełne
wydania (deploy tej samej ścieżką; tag widać na GHCR jako `v0.1.2`).

---

## §8. Rollback

**Ręcznie na VPS:**

```bash
ssh panel-vps 'cd /opt/panel-stage && ./scripts/rollback.sh --list'   # co dostępne
ssh panel-vps 'cd /opt/panel-stage && ./scripts/rollback.sh'           # → last-stable (potwierdzenie TAK)
ssh panel-vps 'cd /opt/panel-stage && ./scripts/rollback.sh --to sha-6a9b6ff --yes'
```

**Z GitHuba: Actions → "Stage release" → Run workflow** → pole `rollback_to`
(np. `sha-6a9b6ff`) — odpala dedykowany job
rollin back na VPS tymi samymi refami.

Semantyka: `last-stable.txt` = pierwszy ostatni release z **zdrowym
healthcheckiem**; rollback nie nadpisuje go (można wrócić ponownie); historia
w `releases.txt`. Warunek: obrazy poprzedniego wydania muszą być osiągalne
(GHCR przechowuje wszystko; w ścieżce ręcznej tagi zostają na VPS).

---

## §9. Checklista „SSO na stage" (kroki właściciela; zero sekretów w transkrypcji)

Cel: włączyć logowanie Microsoft 365 na dev.panel.mlodzirazem.org, gdy
postanowisz. Wszystkie wartości sekretowe WKŁADASZ TY SAM — w plikach na VPS lub w
Entra; nigdy w czacie, commitach ani issue.

**9.1. Entra (portal.azure.com → Microsoft Entra ID → App registrations →
„Panel MR")**:

- [ ] **Redirect URI** dodaj: `https://dev.panel.mlodzirazem.org/api/auth/callback/azure-ad`
      (Authentication → Add URI → Save). Istniejące produkcyjne URI zostają.
- [ ] **Authentication** — zostaw domyślne ustawienia webowanego redirectu;
      panel nie używa public client flows.
- [ ] Ten **tenant blokuje client secrets** (repo: values.yaml tak komentuje).
      W praktyce:
      *Teams SSO (w aplikacji Teams)* działa bez secretu — wystarczą tenant+client ID;
      *logowanie w przeglądarce* wymaga `PANEL_ENTRA_CLIENT_SECRET` — jeśli
      sekrety pozostają zablokowane, na stage zostaje Teams SSO
      (poniżej opisane oba warianty).
- [ ] **Certyfikat** dla panel-api/Graph: Certificates & secrets →
      **Certificates** → Upload (certyfikat, który wygenerujesz; klucz nigdy
      nie opuszcza Twojego sejfu + VPS).
- [ ] **Graph consent**: API permissions `User.Read.All` (Application) →
      *Grant admin consent* (raz, admin).

**9.2. Wartości publiczne** (tenant ID i client ID są publiczne — widnieją
w konfiguracji aplikacji produkcyjnej, patrz AGENTS.md): wpisz je w `.env.stage`:
`PANEL_ENTRA_TENANT_ID`, `PANEL_ENTRA_CLIENT_ID`.

**9.3. Certyfikaty do panel-api — preferowany kanał to plik, nie env**
(wielolinijkowy PEM w `.env`-style jest kruchy). Na VPS:

```bash
cd /opt/panel-stage
nano panel-config/panel-config.yaml   # TY wklejasz certyfikat + klucz z Twojego sejfu
chmod 600 panel-config/panel-config.yaml
```

Zawartość (szkielet — dokładne nazwy properties jak w panel.yml panel-api; klucz w PKCS#8):

```yaml
panel:
  entra:
    tenant-id: "<tenant-id>"
    client-id: "<client-id>"
    client-certificate: |
      -----BEGIN CERTIFICATE-----
      ...

      -----END CERTIFICATE-----
    client-certificate-key: |
      -----BEGIN PRIVATE KEY-----
      ...

      -----END PRIVATE KEY-----
```

(`PANEL_CONFIG_FILE_PATH=/panel-config/panel-config.yaml` jest już ustawiony w
`.env.stage.example`; compose mountuje `panel-config/` read-only.)

**9.4. Panel-web** (dla Teams SSO i ewentualnego logowania w przeglądarce): w
`.env.stage` uzupełnij `PANEL_ENTRA_TENANT_ID`, `PANEL_ENTRA_CLIENT_ID`, a jeśli
masz działający sekret — także `PANEL_ENTRA_CLIENT_SECRET` (razem z
redirect URI z 9.1).

**9.5. Restart i test:**

```bash
ssh panel-vps 'cd /opt/panel-stage && docker compose -f docker-compose.stage.yaml \
  --env-file .env.stage up -d panel-api panel-web && ./scripts/healthcheck.sh'
```

- panel-api: logi bez ostrzeżeń o entra; lookups działają (test wywołaniem
  opisujemy osobno — wg API).
- panel-web: `https://dev.panel.mlodzirazem.org/login` — pojawia się przycisk
  Microsoft/Teams SSO.
- Aplikacja Teams na hoście stage: wymaga osobnego package'u Teams z
  HOST=dev.panel... (teams/package.mjs) — do ulożenia, gdy stage SSO zaakceptujesz.

**9.6. Google (opcjonalnie):** aby ekran logowania Google „żył" jak
produkcja, podstaw w `.env.stage` prawdziwe `PANEL_GOOGLE_CLIENT_ID/SECRET` z
produkcji **oraz** dodaj w Google Cloud Console redirect URI
`https://dev.panel.mlodzirazem.org/api/auth/callback/google`. Bez tej zmiany
przycisk zwróci błąd — i tak jest domyślnie na stage.

---

## §10. Notka: stage bez dev-seedów (GDPR)

- Baza stage startuje **pusta** i dostaje wyłącznie schemat z migracji Flyway
  (V0.0.1–V0.0.5) wraz z **syntetycznymi** fixture'ami organizacyjnymi
  (szkielet struktur, liczniki testowe; zero danych osobowych). Plik
  `dev/seed/test-members.sql` (osoby testowe z numerami 95xxxx) **NIE jest
  aplikowany na stage** — to wyłącznie development.
- Postawa docelowa: stage = dane **syntetyczne** ownera do smoke-testów.
  Jeśli kiedykolwiek wstawisz tam dane prawdziwe (nawet testowe konta
  członków), od tej chwili: baza i backupy `backups/*.sql.gz` zawierają dane
  RODO art. 9 → trzymaj retencję 7 dni/uprawnienia/hasło db silne i nie
  synchronizuj ich nigdzie poza VPS. Rekomendacja niezmieniona:
  **stage wyłącznie dane syntetyczne**.
- Raporty/analizy wypisujemy wyłącznie **agregaty** (liczby rządów / statusy),
  nigdy wiersze z danymi osób.

---

## Appendix A: instalacja Docker Engine od zera (inna maszyna niż obecny VPS)

Obecny VPS ma Dockera (29.10) — ten appendix istnieje na wypadek wymiany
hostu. Oficjalna ścieżka dla Ubuntu (Debian analogicznie):

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
   https://download.docker.com/linux/ubuntu \
   $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
                        docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker nepose   # i przeloguj się!
```

Weryfikacja: `docker run --rm hello-world && docker compose version`.

Inne dystrybucje (skrót): Fedora/RHEL — `dnf install docker-ce
docker-compose-plugin` + `systemctl enable --now docker`, firewall przez
`firewall-cmd`; Alpine — `apk add docker docker-cli-compose` + OpenRC.
Wszędzie: compose-plugin i buildx-plugin są wymagane przez skrypty
`deploy/stage` (wersja ≥ v2.24 dla `up --wait --wait-timeout` i
`env_file.required`).

---

## §11. Tor K: STAGE w microk8s (AD-9 prod-parity) — jeśli wybierzesz

Klaster jest już przygotowany (inwentarz poniżej — niczego NIE instalujesz od
zera; asystent nie wykonał na nim żadnej zmiany). Poniżej kolejność kroków;
**żaden z nich nie wykonuje się sam** — wszystko klikasz/wklejasz Ty, a
deployment następuje dopiero po Twojej zgodzie. Raport („Sekcja K") opisuje
warianty ekspozycji i uzasadnienia.

### 11.1. Inwentarz (zweryfikowany read-only przez właściciela)

| komponent | namespace | uwagi |
|---|---|---|
| cert-manager | cert-manager | dostępny; Issuer/ClusterIssuer brak (stworzy chart) |
| CNPG operator | cnpg-system | obraz klastra pg: `ghcr.io/cloudnative-pg/postgresql:18.1` (wymóg PG18 spełniony) |
| istio | istio-system | GatewayClass `istio` Installed=True; ingressgateway = LoadBalancer MetalLB **10.64.100.1** (publicznie tylko przez NodePort 32011/30308) |
| MetalLB | metallb-system | pula `10.64.100.0/24` — WEWNĘTRZNA; publiczne 80/443 aktualnie NIE są eksponowane |
| kube-prometheus-stack | observability | Prometheus+Grafana+Alertmanager; CRD PodMonitor istnieją (jest też tempo+loki+promtail) |
| otel operator | opentelemetry | wykorzystywany przez chart przy jaeger.enabled=true |
| Origin CA issuer | origin-ca-issuer | kontroler Cloudflare Origin CA; działa |

Weryfikacja na start (odpal i porównaj):

```bash
microk8s kubectl get gatewayclass
microk8s kubectl -n metallb-system get ipaddresspool,l2advertisement
microk8s kubectl -n istio-system get svc istio-ingressgateway -o wide
```

### 11.2. Pliki potrzebne (są w repo — workflow rsyncuje je na VPS)

- `cloud/chart/` — chart (ląduje w `/opt/panel-stage/cloud/chart/`);
- `deploy/stage/microk8s/values.stage-microk8s.yaml` — overlay stage (bez
  sekretów): `jaeger.enabled=false`, storageClass `microk8s-hostpath`,
  GatewayClass `istio`, domena `dev.panel.mlodzirazem.org`, TLS
  `letsencrypt` (HTTP-01 przez gateway; wariant `origin-ca` w §11.5),
  `replicas: "1"`, zasoby zmniejszone pod stage;
- `deploy/stage/microk8s/values.stage-secrets.example.yaml` — szablon sekretów.

**Na VPS** (jednorazowo):

```bash
mkdir -p /opt/panel-stage/microk8s
cp values.stage-secrets.example.yaml /opt/panel-stage/microk8s/values.stage-secrets.yaml
chmod 600 /opt/panel-stage/microk8s/values.stage-secrets.yaml
nano /opt/panel-stage/microk8s/values.stage-secrets.yaml   # podstaw sekrety jak w §2
```

### 11.3. Ekspozycja publiczna 80/443 (JEDYNA rzecz do skonfigurowania w infra)

Pilotaż zweryfikował architekturę ekspozycji (kluczowe!): istio tworzy
**osobny, dedykowany dataplane** dla Gatewaya z chartu — serwis
`panel-gateway-istio` w ns `stage` na LoadBalancerze MetalLB
**10.64.100.2** (NodePorty przydzielane automatycznie). Stary
`istio-ingressgateway` (10.64.100.1, NodePorty 32011/30308) **nie obsługuje**
Gatewaya z chartu — nie wystawiaj przez niego.

**Rekomendacja (sprawdzona w pilotażu): socat na hoście → VIP 10.64.100.2.**
DNAT/iptables odrzucony celowo: kube-proxy na tym klastrze pracuje w trybie
`iptables-legacy`, a ufw w nft — reguły REDIRECT na 80/443 bywają przy tym
podziale zawodne. socat to userspace'owy proxy TCP, zero interakcji z
łańcuchami iptables/ufw; dwie unity (odwracalne: `systemctl disable --now`):

```bash
sudo apt-get install -y socat
# adres VIP sprawdź: microk8s kubectl -n stage get gateway panel-gateway \
#   -o jsonpath='{.status.addresses[0].value}'
sudo tee /etc/systemd/system/panel-stage-expose-http.service >/dev/null <<UNIT
[Unit]
Description=panel stage: expose :80 -> istio Gateway VIP 10.64.100.2 (socat)
After=network-online.target
Wants=network-online.target
[Service]
ExecStart=/usr/bin/socat TCP-LISTEN:80,fork,reuseaddr TCP4:10.64.100.2:80
Restart=always
[Install]
WantedBy=multi-user.target
UNIT
sudo tee /etc/systemd/system/panel-stage-expose-https.service >/dev/null <<UNIT
[Unit]
Description=panel stage: expose :443 -> istio Gateway VIP 10.64.100.2 (socat)
After=network-online.target
Wants=network-online.target
[Service]
ExecStart=/usr/bin/socat TCP-LISTEN:443,fork,reuseaddr TCP4:10.64.100.2:443
Restart=always
[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now panel-stage-expose-http panel-stage-expose-https
sudo ss -tlnp | grep -E ':(80|443) '        # dowód: socat słucha
```

Do tego pasy ufw (pilotaż używa dokładnie tych):

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# cofnięcie: sudo ufw delete allow 80/tcp && sudo ufw delete allow 443/tcp
```

Test ekspozycji publicznej (z pilotażu): `curl -sI http://dev.panel.mlodzirazem.org/`
musi zwrócić 404 z nagłówkiem `server: istio-envoy` — to dowód, że łańcuch
internet → socat → VIP → Gateway działa; cokolwiek innego (timeout, refused)
oznacza przerwę w łańcuchu, nie brak strony.

Warianty alternatywne (MetalLB z pulą publiczną, rozszerzony
node-port-range, hostPort) — w raporcie, sekcja „Ekspozycja publiczna”;
wybierz świadomie, gdyby socat przestał wystarczać (np. ruch rząd tysięcy
rps — wtedy pula publiczna MetalLB dla VIP jest naturalnym następcą).

Sprawdzenie DNS przed TLS (wariant letsencrypt wymaga rekordu BEZ proxowania
Cloudflare — tzw. szary obłok):

```bash
dig +short dev.panel.mlodzirazem.org    # ma zwrócić publiczne IP VPS-a
```

### 11.4. imagePullSecret (obrazy z GHCR są private)

**Na VPS** (raz; `<PAT-READ:PACKAGES>` — Twój token z §6.4 — nigdy przez czat):

```bash
microk8s kubectl create ns stage 2>/dev/null || true
microk8s kubectl -n stage create secret docker-registry ghcr-pull \
    --docker-server=ghcr.io \
    --docker-username=<LOGIN-GITHUB> \
    --docker-password=<PAT-READ:PACKAGES>
```

Overlay ma DOMYŚLNIE puste `panel.{api,web}.imagePullSecret` (neutralne dla
pilotażu offline, gdzie obrazy trafiają do containerd przez `ctr images
import`). Secret jest warstwą per-transport: dla toru GHCR (workflow GHA)
ustaw `ghcr-pull` oboma serwisami — najprościej w kopii overlay na VPS
(`imagePullSecret: ghcr-pull` w sekcjach `api:` i `web:`), ewentualnie
dopisując do wywołania z §11.6:

```text
--set panel.api.imagePullSecret=ghcr-pull --set panel.web.imagePullSecret=ghcr-pull
```

### 11.5. TLS — dwa warianty (rekomendacja: letsencrypt na start)

- **(a) letsencrypt HTTP-01 (rekomendacja stage)**: overlay ma
  `panel.gateway.tls.issuer: letsencrypt` + `acmeEmail`
  (`bartosz.baginski@proton.me`). Chart dokłada listener HTTP:80 na gatewayu
  (dla wyzwań ACME) i `ClusterIssuer` `panel-le-issuer` z solverem
  `gatewayHTTPRoute` (cert-manager tworzy własny HTTPRoute podpinany do
  sekcji `panel-acme` — poprawka z pilotażu; pole `gatewayHTTP01` ze starszych
  wersji nie istnieje w CRD ≥1.18). Wymagania:
  1. rekord DNS bez proxy CF (§11.3),
  2. **cert-manager ≥ 1.18** (schema solvera) i
  3. **flaga controllera `--enable-gateway-api=true`** — UWAGA (zweryfikowane
     bojowo na v1.19.1): to NIE jest feature-gate; `--feature-gates=GatewayAPI=true`
     **nie istnieje** w v1.19.1 i wywala controller w CrashLoop
     (`unrecognized feature gate: GatewayAPI`). Historycznie był gate
     `ExperimentalGatewayAPISupport`, dziś jest zwykła flaga. Bez flagi
     `certificate` utyka w `Ready=False`, a `kubectl describe challenge`
     pokazuje `couldn't Present challenge: gateway api is not enabled`.

Zweryfikuj wersję i args, a jeśli brak flagi — dołóż ją (to krok
OWNERA; platforma AD-9, asystent nie modyfikuje ns cert-manager). Wzór
z pilotażu: JSON patch `op=replace` całej tablicy args (przeszedł na
v1.19.1; deterministyczny i idempotentny):

```bash
microk8s kubectl -n cert-manager get deploy cert-manager \
    -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
microk8s kubectl -n cert-manager get deploy cert-manager \
    -o jsonpath='{.spec.template.spec.containers[0].args}'; echo
# gdy brak "--enable-gateway-api=true" — podmień args na (istniejące + flaga):
microk8s kubectl -n cert-manager patch deployment cert-manager --type='json' \
  -p='[{"op":"replace","path":"/spec/template/spec/containers/0/args","value":["--v=2","--cluster-resource-namespace=$(POD_NAMESPACE)","--leader-election-namespace=kube-system","--acme-http01-solver-image=quay.io/jetstack/cert-manager-acmesolver:v1.19.1","--max-concurrent-challenges=60","--enable-gateway-api=true"]}]'
microk8s kubectl -n cert-manager rollout status deploy/cert-manager
# dowód działania: w logach kontrolera "enabling the sig-network Gateway API
# certificate-shim and HTTP-01 solver", po kilku minutach
# `kubectl -n stage get certificate panel-ssl-cert` → READY=True
```

- **(b) origin-ca (jak prod)**: w overlay zmień `issuer: origin-ca` i podstaw
  `panel.secrets.cloudflare.apiKey` w pliku sekretów; wymaga proxowania
  (pomarańczowy obłok) rekordu `dev.panel...` w Cloudflare i działającego
  kontrolera origin-ca-issuer (w klastrze jest).

### 11.6. Pierwszy deploy (tor K)

Przez CI: w `.github/workflows/stage.yml` ustaw `DEPLOY_VARIANT: helm`
(zmiana w repo po review — to Twoja decyzja), potem `git push origin
release/stage`. Ręcznie na VPS (gdy chcesz bez GitHuba):

```bash
cd /opt/panel-stage
./ci/deploy-helm-on-vps.sh \
    --api-image ghcr.io/<OWNER>/panel-api:<tag> \
    --web-image ghcr.io/<OWNER>/panel-web:<tag>
```

Rezultat: ns `stage` z release `panel-stage`; rollout obu deploymentów +
healthcheck (patrz wyjście skryptu); `last-stable.txt` notuje rewizję helma.

### 11.7. Healthcheck (tor K)

```bash
microk8s kubectl -n stage get pods,svc,gateway,httproute
microk8s kubectl -n stage get certificate panel-ssl-cert \
    -o jsonpath='{.status.conditions[?(@.type=="Ready")].message}'; echo
curl -fsS https://dev.panel.mlodzirazem.org/api/health     # {"status":"ok"}
```

### 11.8. Backup (tor K) — cron z pg_dump przez CNPG

Linia do `crontab -e` (user `nepose`; retencja jak w §5):

```cron
27 3 * * * microk8s kubectl -n stage exec cnpg-1 -- sh -c 'pg_dump -U mrpanel mrpanel' | gzip > /opt/panel-stage/backups/panel-stage-$(date +\%Y\%m\%d).sql.gz && find /opt/panel-stage/backups -name 'panel-stage-*.sql.gz' -mtime +7 -delete >> /opt/panel-stage/backups/backup.log 2>&1
```

Uwaga: `cnpg-1` = nazwa poda (sprawdź `microk8s kubectl -n stage get pods`).
Restore: sekwencja jak w `scripts/restore.sh`, tylko przez `kubectl exec`
(drop schematów + `psql` z rozpakowanego archiwum).

### 11.9. Rollback (tor K)

```bash
microk8s helm -n stage history panel-stage            # lista rewizji
microk8s helm rollback panel-stage -n stage           # do poprzedniej
microk8s helm rollback panel-stage <NR> -n stage      # do konkretnej
```

`ci/deploy-helm-on-vps.sh` używa `--wait` + rollout status; nieudany upgrade
z `--atomic` wycofa się sam (helm). Workflow: *Run workflow* z
`rollback_to` = pusty → `helm rollback`; z tagiem `sha-<hash>` → ponowny
deploy starych obrazów.

### 11.10. Co NIE zmienia się przy przejściu na K

- obrazy: te same Dockerfile'e i GHCR (CI: panel-api z jara po verify,
  `Dockerfile.prebuilt`, §6.6);
- sekrety: te same wartości co tor C; jawny plik różni się formatem
  (values.yaml zamiast .env);
- SSO: checklista §9 działa po przeniesieniu wartości Entra do
  `panel.api.config` (map w pliku sekretów) — szczegóły w raporcie;
- backup/healthcheck: ta sama logika; driver `kubectl exec` zamiast
  `docker compose exec` (§11.8).

### 11.11. Pułapki operacyjne (z pilotażu — czytaj ZANIM zdiagnozujesz „helma wiesza")

1. **Helm ubity w trakcie `--wait` zostawia lock release'a.** Objaw:
   kolejne upgrade kończą się `another operation (install/upgrade/rollback)
   is in progress`, a `helm list --all` pokazuje `pending-install` /
   `pending-upgrade`. Helm najpierw APLIKUJE zasoby i dopiero potem czeka
   (`--wait --timeout 10m`) — przerwany proces czekania zostawia zasoby w
   klastrze + lock w sekretach release'a. Odzyskanie: dobij zombie procesy
   helma (`pgrep -fa "[h]elm"` → `kill -9 <pid>` — trik z nawiasem niżej),
   potem `microk8s helm uninstall panel-stage -n stage` i **świeży
   `upgrade --install`**. Ubity-process ≠ deinstalacja: zasoby zostają,
   dopóki ich nie usuniesz.
2. **`pkill -f` przez ssh zabija własną sesję.** Zdalny shell (`zsh -c`)
   ma Twój wzorzec w cmdline — `pkill -f "helm.*panel"` matchuje sam siebie.
   Trik: zapisz wzorzec z nawiasem klasy znaków (`"[p]anel"`) — regex
   dopasuje `panel` w cudzych cmdline, ale nie literal `[p]anel` w Twoim.
3. **`microk8s ctr` ma wbudowany namespace k8s.io** — NIE podawaj
   `-n k8s.io` (błąd „namespace already exists"/duplikat), NIE gzipuj:
   `docker save OBRAZ | microk8s ctr images import -`. Po imporcie
   z tym SAMYM tagiem kubelet z `imagePullPolicy: IfNotPresent` trzyma
   stary image ID: przy podmianie obrazu pod tym samym tagiem
   **usuń pody** (`kubectl -n stage delete pods -l …`), kubelet dociągnie
   nowy content.
4. **`helm --wait` vs `% Waiting`:** log skryptu deploy pokazuje
   `installing…`/`pending-install` DOPIERO podczas fazy czekania — zasoby
   istnieją już od pierwszych sekund. Nie myl „nic się nie dzieje"
   na żywym release: diagnoza przez `kubectl get pods/events` w ns `stage`,
   nie przez restart helma.
5. **`runAsNonRoot` + nienumeryczny USER w obrazie** → pody w
   `CreateContainerConfigError` (`image has non-numeric user (mrpanel)`).
   Dockerfile'e w repo mają już poprawkę (`USER 10001:10001`); objaw pojawia
   się, gdy ktoś cofnie USER na literal albo zbuduje obraz ze starego źródła.
6. **PVC pre-binding vs dynamiczna klasa:** `cnpgPvcTemplate.volumeName`
   (default chartu: `mrpanel-postgres-pv`) przypina PVC do PV o tej nazwie;
   jeśli PV nie istnieje, PVC wisi `Pending` na zawsze MIMO poprawnego
   `storageClassName` (dynamiczny provisioning nie rusza dla
   volumeName-prebound). Overlay stage ma `volumeName: null` — klucz znika
   z renderu; nie przywracaj go na klastrach z dynamiczną klasą.
