# dev/Makefile — ergonomics for the local development database (see dev/README.md).
# Use from anywhere: `make -C dev db-up` (or `cd dev && make db-up`).
# Targets only touch the panel-dev compose project; the live panel-pg on 5432 stays alone.

DEV_DIR     := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
COMPOSE     := docker compose -f $(DEV_DIR)/compose.yaml
PSQL        := psql -U panel -d panel -v ON_ERROR_STOP=1
SEED_FILE   := $(DEV_DIR)/seed/test-members.sql

.PHONY: help db-up db-down db-psql db-seed db-reset db-logs

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "} {printf "  %-10s %s\n", $$1, $$2}'

db-up: ## Start the dev database (panel-dev db, host port 5433) and wait until healthy
	$(COMPOSE) up -d --wait

db-down: ## Stop the dev database (data volume is kept)
	$(COMPOSE) down

db-psql: ## Interactive psql inside the dev database container
	$(COMPOSE) exec db $(PSQL)

db-seed: ## Apply dev/seed/test-members.sql (idempotent; needs the api booted once first)
	$(COMPOSE) exec -T db $(PSQL) < $(SEED_FILE)

db-reset: ## Destroy the dev database (incl. data volume) and start a fresh, empty one
	$(COMPOSE) down -v
	$(COMPOSE) up -d --wait

db-logs: ## Tail dev database logs
	$(COMPOSE) logs -f db
