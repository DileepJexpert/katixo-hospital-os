#!/usr/bin/env bash
# Dev-phase database reset.
#
# During development we do NOT write ALTER migrations: schema changes are
# edited directly into db/migration/tenant/V1__tenant_baseline.sql (and
# policy seeds into V2__default_policies.sql; platform schema into
# db/migration/platform/V1__tenant_registry.sql), then the database volume
# is recreated. On next app start TenantBootstrap migrates the platform
# schema and re-provisions the demo tenant from the baseline.
#
# Usage: ./scripts/reset-db.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Stopping containers and deleting volumes (postgres data will be wiped)..."
docker compose down -v

echo "Starting fresh postgres + redis..."
docker compose up -d postgres redis

echo "Waiting for postgres to be healthy..."
until docker compose exec -T postgres pg_isready -U katixo > /dev/null 2>&1; do
  sleep 1
done

echo "Done. Start the app — platform schema + demo tenant will be recreated from the baseline."
