# deploy/stage/Makefile — ergonomics for the STAGE environment on the VPS
# (mirrors the style of dev/Makefile; see deploy/stage/README.md).
#
# On the VPS:  cd /opt/panel-stage && make up      (also works via make -C)
# From a dev Mac (config validation only):  make -C deploy/stage config
#
# Env files:  .env.stage is the owner's file; release.env is written by
# ci/deploy-on-vps.sh on every GitHub-driven release and points compose at the
# GHCR images of the current release. Both are used when present, so `make up`
# keeps working no matter which release path was used last.

STAGE_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
REPO_ROOT := $(abspath $(STAGE_DIR)/../..)

# Before the owner creates .env.stage, commands fall back to the example env
# (placeholders only), so `make config` works out of the box.
ENV_FILE := $(if $(wildcard $(STAGE_DIR)/.env.stage),.env.stage,.env.stage.example)
# release.env joins in only after the first deploy from CI (GHCR refs).
RELEASE_ENV := $(if $(wildcard $(STAGE_DIR)/release.env),--env-file release.env,)

COMPOSE := docker compose -f docker-compose.stage.yaml --env-file $(ENV_FILE) $(RELEASE_ENV)

# Commands that need live values fail with a hint instead of deploying
# placeholders (RUNBOOK §2).
define REQUIRE_LIVE
	@if [ ! -f "$(STAGE_DIR)/.env.stage" ]; then \
	    echo "Brak .env.stage w $(STAGE_DIR) — utwórz wg deploy/stage/RUNBOOK.md §2."; \
	    exit 78; \
	fi
endef

.PHONY: help config up down ps logs psql health backup restore rollback build-api build-web release init-remote

help: ## Pokaż tę pomoc
	@cd "$(STAGE_DIR)" && grep -E '^[a-zA-Z0-9_-]+:.*?## ' Makefile | awk 'BEGIN {FS = ":.*?## "} {printf "  %-14s %s\n", $$1, $$2}'

config: ## Zwaliduj compose (na Macu działa na przykładzie env)
	@cd "$(STAGE_DIR)" && STAGE_ENV_FILE=$(ENV_FILE) $(COMPOSE) config -q \
	    && echo "OK: compose config przechodzi ($(ENV_FILE))."

up: ## Start stacku (panel-api robi migracje Flyway przy starcie)
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && STAGE_ENV_FILE=$(ENV_FILE) $(COMPOSE) up -d --wait --wait-timeout 420

down: ## Zatrzymaj stage (dane zostają na wolumenie)
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && STAGE_ENV_FILE=$(ENV_FILE) $(COMPOSE) down

ps: ## Stan kontenerów
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && STAGE_ENV_FILE=$(ENV_FILE) $(COMPOSE) ps

logs: ## Podążaj za logami (Ctrl-C kończy)
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && STAGE_ENV_FILE=$(ENV_FILE) $(COMPOSE) logs -f --tail 200

psql: ## Interaktywne psql w kontenerze postgres (dane logowania z env kontenera)
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && STAGE_ENV_FILE=$(ENV_FILE) $(COMPOSE) exec postgres \
	    sh -c 'exec psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

health: ## End-to-end healthcheck (TLS przez Caddy + actuator)
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && ./scripts/healthcheck.sh

backup: ## Zrzut bazy do backups/ + retencja 7 dni (używane też przez cron)
	$(REQUIRE_LIVE)
	@cd "$(STAGE_DIR)" && ./scripts/backup.sh

restore: ## Przywróć backup: make restore FILE=backups/panel-stage-*.sql.gz
	$(REQUIRE_LIVE)
	@if [ -z "$(FILE)" ]; then echo "Podaj FILE=<plik.sql.gz>"; exit 64; fi
	@cd "$(STAGE_DIR)" && ./scripts/restore.sh "$(FILE)" --yes

rollback: ## Rollback do last-stable (albo: make rollback TO=sha-abc1234)
	$(REQUIRE_LIVE)
	@if [ -n "$(TO)" ]; then \
	    cd "$(STAGE_DIR)" && ./scripts/rollback.sh --to "$(TO)"; \
	else \
	    cd "$(STAGE_DIR)" && ./scripts/rollback.sh; \
	fi

build-api: ## Zbuduj lokalnie obraz panel-api (tag = krótki hash HEAD; Mac)
	docker build --build-arg GIT_TAG=$$(cd "$(REPO_ROOT)" && git rev-parse --short HEAD) \
		-t panel-api:$$(cd "$(REPO_ROOT)" && git rev-parse --short HEAD) \
		-f "$(REPO_ROOT)/services/panel-api/Dockerfile" "$(REPO_ROOT)"

build-web: ## Zbuduj lokalnie obraz panel-web (tag = krótki hash HEAD; Mac)
	docker build --build-arg GIT_TAG=$$(cd "$(REPO_ROOT)" && git rev-parse --short HEAD) \
		-t panel-web:$$(cd "$(REPO_ROOT)" && git rev-parse --short HEAD) \
		-f "$(REPO_ROOT)/services/panel-web/Dockerfile" "$(REPO_ROOT)/services/panel-web"

release: ## Ręczna ścieżka awaryjna: cały release z Maca (build→save→ssh→up)
	"$(REPO_ROOT)/ci/release-local.sh" $(ARGS)

init-remote: ## Pierwsza synchronizacja deploy/stage na VPS (idempotentna)
	"$(REPO_ROOT)/ci/release-local.sh" --init $(ARGS)

.DEFAULT_GOAL := help
