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
| `seed/test-members.sql` | idempotent seed: two fabricated test members with PRIMARY affiliations |
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

## 4. Seed test data (idempotent)

After the api booted once (tables exist):

```bash
make -C dev db-seed
# or:
docker compose -f dev/compose.yaml exec -T db psql -U panel -d panel -v ON_ERROR_STOP=1 \
  < dev/seed/test-members.sql
```

- Two fabricated members — `000205` "Anna Testowa" and `000368` "Barbara
  Przykładowa" — each with a `PRIMARY` district affiliation to Okręg
  Mazowiecki (its `bodies` id is looked up **by name**, so id changes don't
  break the seed).
- Safe to run any number of times: `ON CONFLICT DO NOTHING` inside one
  transaction; re-runs leave counts and existing ids untouched.
- Order matters only the first time: seed before the first api boot fails with
  `relation "members" does not exist` (the schema does not exist yet).

Verify the seed:

```bash
docker compose -f dev/compose.yaml exec db psql -U panel -d panel \
  -c "SELECT m.member_id, m.preferred_name_first || ' ' || m.preferred_name_last AS name, m.status, b.name AS district, a.kind, a.votes_here
      FROM members m
      JOIN member_district_affiliations a ON a.member_id = m.id
      JOIN bodies b ON b.id = a.district_body_id
      ORDER BY m.member_id;"
```

Expected: two rows, districts `Okręg Mazowiecki`, kind `PRIMARY`.

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
