# dev/ — reproducible local development database for panel-api

This directory replaces the ad-hoc manual database setup. It runs **PostgreSQL
18.1 Alpine** in its own compose project:

| | this stack | live test DB (do not touch) |
|---|---|---|
| container | `panel-dev-db` | `panel-pg` |
| compose project / prefix | `panel-dev` | — (plain `docker run`) |
| host port | **5433** | 5432 |
| credentials | `panel` / `panel` (dummy) | `panel` / `panel` |

All commands below assume the repository root (`panel-teams-app`) as the working
directory. Nothing here ever stops, restarts or writes to the live `panel-pg` on 5432.

## Files

| file | purpose |
|---|---|
| `compose.yaml` | the database service, its volume, healthcheck and names |
| `seed/test-members.sql` | legacy fabricated members `000205`/`000368` (predate the test-band convention) |
| `seed/test-personas.sql` | recruitment personas + edge statuses, reserved test band 950000+ (see sections 4 and 7) |
| `env.example` | dummy environment values for the api (with the `$(cat ...)` patterns for certs) |
| `Makefile` | `db-up` / `db-down` / `db-psql` / `db-seed` / `db-reset` / `db-logs` shorthands |

## 1. Start, stop, reset the database

```bash
make -C dev db-up      # up -d --wait (waits for the pg_isready healthcheck)
make -C dev db-down    # stop; the data volume is kept
make -C dev db-reset   # down -v && up -d --wait: fresh, EMPTY database
```

Plain compose equivalents:

```bash
docker compose -f dev/compose.yaml up -d --wait
docker compose -f dev/compose.yaml down        # keep data
docker compose -f dev/compose.yaml down -v     # destroy data volume (reset)
```

## 2. Migrations: they run when the API boots — NOT from compose

**This compose file creates an *empty* database.** The schema (Flyway
migrations `0.0.1` → `0.0.5`, the organisation/bodies seed — including
"Okręg Mazowiecki" — and the modulith `events` schema) is created **by
`panel-api` itself, automatically, the first time it boots against the
database**. There is no `docker compose` step that runs migrations.

So the first-time order is always: **start DB → boot the api against it (point
3) → then seed (point 4)**.

Verify migrations ran (only meaningful after an api boot):

```bash
docker compose -f dev/compose.yaml exec db psql -U panel -d panel \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Expected: 15 rows (`0.0.1.00` … `0.0.5.00`), all `success = t`. Quick sanity
checks that also only work after the api booted:

```bash
docker compose -f dev/compose.yaml exec db psql -U panel -d panel \
  -c "SELECT id, name, code FROM bodies WHERE name = 'Okręg Mazowiecki';"
# id 13 in a freshly migrated DB (see seed file header for why we never rely on that)
docker compose -f dev/compose.yaml exec db psql -U panel -d panel -c "\dn"   # shows the events schema
```

## 3. Point the api at this database

The api runs in Docker and reaches the dev DB through the host at port 5433
(`host.docker.internal`). Host ports `18080`/`19080` are mapped here because the
live test api already holds 8080/9080; change them when those are free. All
values shown are dummies from `dev/env.example`.

```bash
docker run -d --name panel-dev-api \
  --add-host=host.docker.internal:host-gateway \
  -m 1g \
  -p 18080:8080 -p 19080:9080 \
  -e JDBC_URL='jdbc:postgresql://host.docker.internal:5433/panel' \
  -e JDBC_USER=panel \
  -e JDBC_PASSWORD=panel \
  -e PANEL_API_AUTH_SECRET='devdummy-secret-0123456789-abcdefghijklmnop' \
  -e PANEL_ENTRA_TENANT_ID='9e97a4dc-d20d-453b-a3ae-eadfc4c7844b' \
  -e PANEL_ENTRA_CLIENT_ID='6b106e9e-8d52-48a3-b09e-52985110f6c6' \
  -v "$PWD/services/panel-api/target/panel-api-0.0.0-MRPANEL-SNAPSHOT.jar:/app/panel-api.jar:ro" \
  eclipse-temurin:25-jre \
  java -XX:MaxRAMPercentage=75 -jar /app/panel-api.jar
```

- `--add-host=host.docker.internal:host-gateway` maps the host alias explicitly.
  On this machine's colima Docker the alias already resolves without it; the flag
  is kept so the command also works on setups where plain Docker Engine needs
  the mapping. With Docker Desktop it is optional but harmless.
- `-m 1g` (plus `MaxRAMPercentage=75`) keeps the api's footprint predictable
  next to the live test session — verified to boot a fresh DB in ~8 s with it.
- `PANEL_API_AUTH_SECRET` must be ≥ 32 bytes — the dummy above is one; real
  secrets are never written into files.
- Entra certificate credentials are **passed by you, from your own terminal
  only** — never stored in this repo. When you need Entra locally, add:

```bash
  -e PANEL_ENTRA_CLIENT_CERTIFICATE="$(cat ~/panel-certs/panel.crt)" \
  -e PANEL_ENTRA_CLIENT_CERTIFICATE_KEY="$(cat ~/panel-certs/panel.key)" \
```

  (docker `--env-file` does not expand `$(cat ...)`, which is why these two are
  `-e` flags and not lines in `env.example`.) Without them Entra stays
  unconfigured and everything else works.

The first boot migrates the empty database (watch it with
`docker logs -f panel-dev-api`); a healthy api afterwards:

```bash
curl -s http://localhost:19080/actuator/health   # {"status":"UP", ...} means migrations are done
```

## 4. Seed test people (idempotent)

After the api booted once (tables exist). `make -C dev db-seed` applies **every
`dev/seed/*.sql` in name order**; to apply a single file:

```bash
docker compose -f dev/compose.yaml exec -T db psql -U panel -d panel -v ON_ERROR_STOP=1 \
  < dev/seed/test-personas.sql
```

| seed file | people |
|---|---|
| `test-members.sql` (legacy) | `000205` "Anna Testowa", `000368` "Barbara Przykładowa" — PRIMARY affiliation to Okręg Mazowiecki. They **predate the reserved-band convention** (section 7) and stay untouched: the live SSO test session depends on them. |
| `test-personas.sql` | role + edge-case personas, all ids in the fixture band 950000–969999 (see section 7 for the full band map) — see the file header for the full persona table, statutory constants (initial period 62–76 days, admission resolution ≤ 7 days, ages 16–26 / SUPPORTING 16+, the `MemberRules` 14+ divergence) and ad-hoc verification queries |

Persona overview (`test-personas.sql`):

| member id | synthetic name | purpose | body / role (permission group) |
|---|---|---|---|
| 950001 | Adam Testowy | ZO Mazowieckiego operator | `Zarząd Okręgu Mazowieckiego` / COORDINATOR (District board, LOCAL) |
| 950002 | Beata Przykładowa | ZO Mazowieckiego collegiality (kolegialność) | same body / MEMBER (District board, LOCAL) |
| 950003 | Cezary Testowy | permission isolation between districts | `Zarząd Okręgu Dolnośląskiego` / COORDINATOR (District board, LOCAL) |
| 950004 | Dorota Przykładowa | ZK approvals | `Zarząd Krajowy` / CO_CHAIR (National board, GLOBAL) |
| 950005 | Edmund Testowy | ZK approvals + fees touchpoint | `Zarząd Krajowy` / TREASURER (National board treasurer, GLOBAL) |
| 950006 | Felicja Skarbnicowa | district treasurer: ZO member rights plus fees | `Zarząd Okręgu Mazowieckiego` / TREASURER (District treasurer, LOCAL) |
| 950007 | Grażyna Probacyjna | initial-period clock: declaration 65 days ago → assessment window 62–76 open now | — (status `PROBATION`) |
| 950008 | Henryk Deklaracyjny | pending declaration (10 days ago) | — (status `FORM_REQUEST`) |
| 950009 | Irena Zawieszona | suspension issued by ZO Mazowieckiego (`suspensions` row, signature `ZO/MZ/01/2026`) | — (status `SUSPENDED`) |
| 950010 | Janusz Wspierający | adult supporting member | — (`SUPPORTING`) |
| 950011 | Katarzyna Rocznikowa | turns 26 in December of the current year — "ends this year" report | — (ACTIVE) |
| 950012 | Leon Wykreślony | removed member (`removed_at` 14 days ago) | — (status `REMOVED`) |

- Both files are safe to run any number of times (`ON CONFLICT WHERE ... DO NOTHING`
  / `NOT EXISTS`, one transaction each); bodies, templates and member types are
  looked up **by name/code, never by id**.
- Row counts after both files: **members 14** (2 legacy + 12 personas),
  **member_district_affiliations 7**, **body_memberships 6**, **suspensions 1**,
  **member_emails 14**. Post-seed checks for roles/affiliations/suspension are
  at the end of `test-personas.sql`.
- Order matters only the first time: seeding before the first api boot fails
  with `relation "members" does not exist` (the schema does not exist yet).
- Candidates for the recruitment module are intentionally **not** seeded here:
  per the members contract a candidate never gets a `members` row before the
  `PRZYJĘTA` decision — their fixtures belong to the future
  `recruitment_cases` / `recruitment_case_events` / `recruitment_decisions`
  tables that migration `0.0.6` will introduce.

Verify the members/affiliations:

```bash
docker compose -f dev/compose.yaml exec db psql -U panel -d panel \
  -c "SELECT m.member_id, m.preferred_name_first || ' ' || m.preferred_name_last AS name, m.status, b.name AS district, a.kind, a.votes_here
      FROM members m
      JOIN member_district_affiliations a ON a.member_id = m.id
      JOIN bodies b ON b.id = a.district_body_id
      ORDER BY m.member_id;"
```

Expected: seven rows — two legacy plus 950001/950002/950006/950009 in
`Okręg Mazowiecki` and 950003 in `Okręg Dolnośląski`, kind `PRIMARY`.

## 5. Connect

```bash
make -C dev db-psql
# or:
docker compose -f dev/compose.yaml exec db psql -U panel -d panel
```

From the macOS host, the database also listens on `localhost:5433`.

## 6. Build / verify the api with Docker-Maven

The Mac has no local JDK/Maven. The known-good verification command (tuned for
this machine's memory) runs Maven in Docker, reuses the `maven-home-panel`
volume for `~/.m2`, and mounts the docker socket so Testcontainers works. On
this machine Docker is colima, with its socket at
`~/.colima/default/docker.sock` (use `/var/run/docker.sock` with Docker
Desktop):

```bash
docker run --rm \
  -v "$PWD":/workspace -w /workspace \
  -v maven-home-panel:/root/.m2 \
  -v "$HOME/.colima/default/docker.sock":/var/run/docker.sock \
  --user 501:20 --group-add 991 \
  -e MAVEN_OPTS=-Xmx320m \
  maven:3.9-eclipse-temurin-25 \
  mvn -pl services/panel-api -am verify -DargLine="-Xmx640m -XX:MaxMetaspaceSize=320m -XX:MaxDirectMemorySize=96m -XX:+UseSerialGC"
```

Testcontainers starts its own throwaway postgres containers through the mounted
socket; it does not touch this dev stack or the live `panel-pg`.

## 7. Numbers, test people, trial candidates and account management

This section answers the owner's question — *"jakie UID dawać? może inna
domena?"* (which ids; maybe a different domain?) — and records the owner's
decision that the reserved band and the `(PRÓBNY)` marker must **also cover
real candidates in the introductory period** (okres wstępny). The band split
below is the working convention, pending the owner's final nod.

### 7.1 The id bands (one six-digit space, three meanings)

Real membership numbers and Entra UPNs share one plain six-digit space
(`<number>@mlodzirazem.org`). Migration `0.0.6` caps the member-number
allocator (`seq_member_number_allocation`, `MAXVALUE 949999`), so the database
physically cannot mint a real number ≥ 950000:

| band | meaning | purge-by-band predicate? |
|---|---|---|
| **000001–949999** | real member numbers — the only range the allocator may ever return | never — real data |
| **950000–969999** | synthetic fixtures and dev accounts (all `dev/seed` personas live here: 950001–950012) | **yes — this is the only sweepable subrange** |
| **970000–999999** | **provisional ids of REAL trial-period candidates** (okres wstępny) — UPN/employeeId only, **no `members` row until PRZYJĘTA** | **never — real people** |

Guarantees: no collision between any species (allocator capped; fixtures ≤
969999; trial ids ≥ 970000); `95xxxx` marks disposable fixtures in logs,
dumps and exports (`member_id LIKE '95%'`), while `97xxxx–99xxxx` marks a
real person mid-process who is in **no member table at all**. The two legacy
fixtures `000205`/`000368` (`seed/test-members.sql`) predate the convention
and stay exactly as they are — a live SSO test session against the tenant
already depends on them (see section 4). Do not renumber them.

### 7.2 Entra-side conventions when creating an account

Tenant operations stay with the owner (their own clicks / PowerShell — never
automated from this repo). The same shape covers both dev test people and
real trial candidates; the **id range** tells them apart:

| attribute | dev test person (950000–969999) | real trial candidate (970000–999999) | why |
|---|---|---|---|
| UPN | `<id>@mlodzirazem.org`, e.g. `950001@mlodzirazem.org` | `<id>@mlodzirazem.org`, e.g. `970003@mlodzirazem.org` | same UPN shape as real accounts; the number is the join key |
| display name | `<fake name> (PRÓBNY)`, e.g. `Adam Testowy (PRÓBNY)` — **suffix**, softer than a prefix; owner's convention | `<candidate's real name> (PRÓBNY)` — **the owner extended the marker to real candidates**, so nobody in address books looks "final" before a uchwała | unmistakable in address books and admin lists |
| employeeId | the same band id, e.g. `950001` | the same provisional id, e.g. `970003` | the api links an Entra account to a member **by employeeId == number**, so SSO linking works for trial accounts exactly as for members |
| accountEnabled | `true` | `true` | the resolver only links enabled accounts |

### 7.3 Real trial candidates: lifecycle and the PRZYJĘTA rename

A candidate applies, is assessed and sits through okres wstępny **outside**
`members` (per the members contract, a `members` row is created only at
PRZYJĘTA). The provisional id exists so they can sign into Panel — during the
trial period they hold the rights of a supporting person, and mail reaches
them:

1. **At DEKLARACJA DOSZŁA** (declaration received) a trial account is created
   with the conventions above; the `recruitment_cases` entry runs the clock
   (62–76 days). No `members` row exists yet. Linking the provisional id /
   Entra object to the case has no column in `0.0.6` yet — it belongs to the
   e-declaration work (see the notes on future migrations in section 4).
2. **At PRZYJĘTA** the case closes into a real member: the allocator mints a
   real number (≤ 949999), the member row is created through the members
   module, and the **trial account is renamed** — UPN and employeeId change
   from the provisional id to the real member number. SSO re-links by
   employeeId; the link is audited like every other identity link. `(PRÓBNY)`
   leaves the display name with the rename.
3. **At REJECTED** the case closes and, **30 days later**, the trial account
   is deleted/anonymized tenant-side while the case row is anonymized
   (recruitment `0.0.6` models this: `anonymized_at` + checks that wipe all
   candidate contact data). The 30-day figure is the owner's decision, synced
   with the retention question (PO-3) — see 7.5.

**RODO interplay.** A provisional id, the Entra account and everything a
candidate types into the declaration are **personal data of a real person**
processed before any membership exists. The information-duty bases claimed
for members (art. 6 ust. 1 lit. b/c RODO; art. 9 ust. 2 lit. d for the
special-category items: preferred name, gender) extend to candidates, but
retention for rejected candidates and having an account before PRZYJĘTA are
decisions the statute assigns to a ZK uchwała (szczegółowe przepisy o
zarządzaniu danymi [art. 21 ust. 2 pkt 4]; szczegółowe zasady okresu wstępnego
[art. 9 ust. 6]). The band keeps all of this machine-checkable: fixtures are
provably fake, trial ids provably provisional, member ids provably final.

### 7.4 Phase-2 option: a dedicated domain (NOT decided)

A harder boundary would put test *and* trial accounts in their own domain —
either a subdomain of the verified org domain (`test.mlodzirazem.org`) or the
tenant's default `*.onmicrosoft.com` suffix.

- **Pros:** hard visual *and* technical separation — e.g.
  `Get-MgUser -Filter endsWith(userPrincipalName,'test.mlodzirazem.org')`
  finds them; cleanup becomes "delete everything under the suffix" without
  per-account review; such accounts never surface next to real people in
  Teams/Outlook pickers; the api needs **no** change, since the resolver links
  by `employeeId`, not UPN suffix.
- **Cons:** extra Entra objects and a (small, but real) verification step for
  the subdomain; mail routing and licensing must be decided — MX still points
  at Google today and licensing comes from the org's limited F1 pool (F1
  treated as including mail and Office); more tenant operations overall.
- **Decision:** deferred to the owner — nothing here implements it; 7.2 works
  with either choice (employeeId drives the link). Note the trial-candidate
  lifecycle (7.3) argues for keeping real candidates on the org domain:
  renaming their UPN at PRZYJĘTA is then a one-step operation within one
  domain.

### 7.5 Cleanup between cycles (proposal)

- **Dev database:** nothing is worth preserving. `make -C dev db-reset`
  destroys the stack's data volume (a fresh, empty postgres) and
  `make -C dev db-seed` recreates every test person deterministically. Seed
  rows are disposable: never migrate, back up or hand-repair them — reset.
- **Entra, fixtures:** accounts for **950000–969999 only** may be swept in
  bulk — employeeId in that range, and/or display name `(PRÓBNY)` **plus**
  the id-range filter (the suffix alone no longer proves a fake person, see
  7.2/7.3). Whether that is one owner-run PowerShell command or per-account
  review is the owner's call.
- **Entra, trial candidates (970000–999999):** never swept by band; their
  cleanup is the 30-days-after-REJECTED rule (7.3), proposed to become part
  of the ZK internal act.
- **GDPR:** dev personas are synthetic — invented, descriptive surnames that
  resemble no real person, fictional birth dates, fixture-band numbers.
  Trial candidates are real people: collect the minimum the process needs,
  keep it machine-local, and let the anonymization checks (recruitment
  `0.0.6`) plus the tenant-side 30-day rule carry the retention decision.

## Troubleshooting

| symptom | cause / fix |
|---|---|
| `syntax error at or near "VIRTUAL"` while the api migrates | the database is not PostgreSQL 18 — check `docker ps` (this stack pins `postgres:18.1-alpine`) |
| seed fails: `relation "members" does not exist` | the api has not booted against this DB yet — boot it once, then seed |
| api fails to connect, "UnknownHost" for `host.docker.internal` | missing `--add-host=host.docker.internal:host-gateway` (colima/plain Engine) |
| `port is already allocated` on 5433 | another stack took 5433 — change the left side of `"5433:5432"` in `compose.yaml` |
| DB was reset (`db-reset`) | the api will re-migrate on its next boot, and the seed must be applied again |

**Cleaning up the throwaway api container:** `docker rm -f panel-dev-api`.
The database keeps running independently.

## What was verified here (2026-11-22, throwaway dry run)

`docker compose config` validates. A full throwaway cycle — `up -d --wait`,
api boot from `services/panel-api/target`, seed ×2, `down -v` — was run against
this compose file and came out as documented:

- api boots in ~8 s and Flyway applies **15/15** migrations
  (`0.0.1.00` → `0.0.5.00`) to the empty database, `flyway_schema_history` all
  `success`; `events` schema (modulith) created by the app; health `UP`;
- Okręg Mazowiecki resolves to `bodies.id = 13` on a fresh DB, and the seed
  finds it **by name**, not by id;
- seed runs are idempotent: first run `INSERT 0 2` twice, re-runs
  `INSERT 0 0` twice; `members=2`, `member_district_affiliations=2`,
  `member_emails=2` (sync trigger) stable across three consecutive runs.

Not verified here: the live `panel-pg` on 5432 was never read, stopped or
otherwise touched; this stack never connected to it.

### Second dry run, 2026-11-22 (personas, no api boot)

On the `feat/recruitment` worktree, without booting the api jar (parallel
Maven builds were running — the 15 migration files were applied by hand with
psql, harness-only; Flyway itself had already been proven in the first run
above):

- both seed files applied by `make -C dev db-seed` (name order),
  and `test-personas.sql` three times: first `INSERT 0 12` (members),
  `0 5` (affiliations), `0 6` (memberships), `0 1` (suspension) — then
  `INSERT 0 0` ×4 on every re-run; counts stable at
  **members 14, affiliations 7, memberships 6, suspensions 1, member_emails 14**;
- role/permission matrix resolved exactly as designed (950001/950002/950003
  District board LOCAL, 950004 National board, 950005 National board
  treasurer GLOBAL, 950006 District treasurer LOCAL — bodies/templates joined
  **by name/code**);
- initial-period fixture: declaration 65 days ago → assessment window 62–76
  open on the day of the run; suspension row carries decision signature
  `ZO/MZ/01/2026` issued by `Zarząd Okręgu Mazowieckiego` (looked up by name);
- `down -v` afterwards; the live `panel-pg` was never touched.
- Still pending jointly: a full **api-boot + seed** cycle against the dev
  stack after migration `0.0.6` (recruitment) lands.
