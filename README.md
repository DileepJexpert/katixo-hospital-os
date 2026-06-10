# Katixo Platform

Katixo Hospital OS + Katixo ERP — monorepo for hospital management platform.

## Services
- `katixo-hospital-service/` — Hospital operations (patient, OPD, IPD, lab, OT, billing, dashboard)
- `katixo-erp-service/` — Existing ERP (pharmacy, stock, GST, payment, ledger)
- `katixo-integration-service/` — Future (WhatsApp, ABDM, AI)

## Shared Libraries
- `katixo-common-lib/` — Base response, exceptions, pagination, utilities
- `katixo-security-lib/` — JWT, auth, current user, permission helpers
- `katixo-tenant-lib/` — Tenant/group/branch context and filters
- `katixo-erp-client/` — Typed ERP internal API client for hospital service

## Frontend
- `katixo-hospital-app/` — Flutter Web responsive app

## Running Locally
```bash
docker-compose up -d
```
