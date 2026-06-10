#!/bin/bash
# Katixo Hospital OS - Repository Scaffold
# Run this to create the full project structure

ROOT="katixo-platform"
mkdir -p $ROOT
cd $ROOT

# ── Root files ──
cat > README.md << 'EOF'
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
EOF

# ── Docs structure ──
mkdir -p docs/design docs/api docs/erd docs/screen-specs docs/sprint-backlog

cat > docs/design/README.md << 'EOF'
# Design Documents
Per-module design specs. Claude Code reads these when implementing features.

- `architecture.md` — System overview, service boundaries, integration patterns
- `opd.md` — OPD workflow, queue, consultation, prescription
- `ipd.md` — Admission, bed, nursing, discharge
- `pharmacy-erp.md` — Pharmacy dispense, ERP integration, OTC
- `billing.md` — Tariff, charges, packages, payment, credit, discount
- `lab-radiology.md` — Lab/RIS order, report, approval, sharing
- `ot.md` — OT scheduling, consent, surgery note, consumables
- `tpa.md` — TPA lifecycle, documents, reminders, ageing
- `consent-certificates.md` — Consent templates, certificate generation
- `nabh.md` — Quality indicators, incident reporting, checklists
- `dashboard-reports.md` — Real-time dashboard, read model, 8 reports
- `cross-cutting.md` — Policy engine, notifications, offline, i18n, printers
- `security.md` — RBAC, audit, masking, MFA, DPDP
EOF

cat > docs/api/README.md << 'EOF'
# API Contracts
OpenAPI/Swagger specs for all API surfaces.

- `hospital-service-api.yaml` — External APIs (Flutter calls these)
- `erp-internal-api.yaml` — Internal APIs (hospital service calls ERP)
- `integration-api.yaml` — Future integration service APIs
EOF

cat > docs/erd/README.md << 'EOF'
# Entity Relationship Design
Flyway migration SQL files serve as the living ERD.
See `katixo-hospital-service/src/main/resources/db/migration/`
EOF

cat > docs/screen-specs/README.md << 'EOF'
# Screen Specifications
Text-based screen specs for Claude Code to build Flutter screens from.
One file per role/module.

- `front-desk.md` — Registration, OPD visit, token, admission
- `doctor.md` — Worklist, consultation, prescription, discharge summary
- `nurse.md` — Ward dashboard, indent, vitals
- `pharmacist.md` — Prescription queue, dispense, OTC sale, return
- `lab-tech.md` — Order list, sample collection, result entry, report
- `radiology-tech.md` — Order list, report entry
- `billing.md` — Collection, refund, discount, final bill, package
- `ot-scheduler.md` — OT booking, checklist, consent
- `tpa-coordinator.md` — Preauth, documents, queries, claims
- `owner-admin.md` — Dashboard, reports, settings, users
EOF

cat > docs/sprint-backlog/README.md << 'EOF'
# Sprint Backlog
Task-sized specs for Claude Code sessions.
Each file = one focused Claude Code session.

Format per task:
- Context: which design docs to read
- What to implement
- Acceptance criteria
- Tests required
EOF

# ── Hospital Service ──
HS="katixo-hospital-service"
mkdir -p $HS/src/main/java/com/katixo/hospital/{config,common,policy,auth,tenant,audit,outbox,idempotency,erpclient}
mkdir -p $HS/src/main/java/com/katixo/hospital/{patient,opd,prescription,ipd,nursing,discharge}
mkdir -p $HS/src/main/java/com/katixo/hospital/{lab,radiology,ot,pharmacy,billing,tpa}
mkdir -p $HS/src/main/java/com/katixo/hospital/{consent,certificate,nabh,dashboard,notification,report}
mkdir -p $HS/src/main/resources/db/migration
mkdir -p $HS/src/test/java/com/katixo/hospital

cat > $HS/src/main/resources/application.yml << 'EOF'
spring:
  application:
    name: katixo-hospital-service
  datasource:
    url: jdbc:postgresql://localhost:5432/katixo_hospital
    username: katixo
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: hospital
  flyway:
    enabled: true
    schemas: hospital,audit
    locations: classpath:db/migration
  data:
    redis:
      host: localhost
      port: 6379
  elasticsearch:
    uris: http://localhost:9200

server:
  port: 8081

katixo:
  erp:
    base-url: http://localhost:8080
    service-token: ${ERP_SERVICE_TOKEN}
  security:
    jwt-secret: ${JWT_SECRET}
  tenant:
    default-branch-id: ${DEFAULT_BRANCH_ID:1}
EOF

# ── Shared Libraries ──
for lib in katixo-common-lib katixo-security-lib katixo-tenant-lib katixo-erp-client; do
  mkdir -p $lib/src/main/java/com/katixo/${lib//-//}
  mkdir -p $lib/src/test/java/com/katixo/${lib//-//}
done

# ── ERP Service (existing — placeholder) ──
mkdir -p katixo-erp-service/src/main/java/com/katixo/erp
mkdir -p katixo-erp-service/src/main/resources

# ── Integration Service (future — placeholder) ──
mkdir -p katixo-integration-service/src/main/java/com/katixo/integration
mkdir -p katixo-integration-service/src/main/resources

# ── Flutter App ──
FA="katixo-hospital-app"
mkdir -p $FA/lib/{app,core,modules}
mkdir -p $FA/lib/core/{auth,api,theme,error,permissions,i18n,offline}
mkdir -p $FA/lib/modules/{front_desk,doctor,nurse,pharmacy,lab,radiology,billing,ipd,ot,tpa,owner,settings}

cat > $FA/lib/app/README.md << 'EOF'
# Katixo Hospital App
Flutter Web responsive app.

## Module Structure
Each module = one role's screens.
- front_desk/ — Registration, OPD visit, token, admission
- doctor/ — Worklist, consultation, prescription
- nurse/ — Ward dashboard, indent, vitals
- pharmacy/ — Prescription queue, dispense, OTC
- lab/ — Orders, samples, results, reports
- radiology/ — Orders, reports
- billing/ — Collection, refund, packages, final bill
- ipd/ — Admission, bed board, transfers
- ot/ — OT scheduling, checklists
- tpa/ — Preauth, documents, claims
- owner/ — Dashboard, reports
- settings/ — Hospital setup, masters, users, policies

## Core
- auth/ — JWT, login, current user
- api/ — HTTP client, error handling, retry
- theme/ — Design tokens, responsive breakpoints
- permissions/ — Role-based UI rendering
- i18n/ — Translation layer
- offline/ — Local storage, sync engine
EOF

# ── Docker Compose ──
cat > docker-compose.yml << 'YAML'
version: '3.8'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: katixo
      POSTGRES_PASSWORD: katixo_dev
      POSTGRES_DB: katixo_hospital
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"

  # Kafka (enable when ready for eventing)
  # redpanda:
  #   image: redpandadata/redpanda:v24.1.1
  #   command: redpanda start --smp 1 --memory 512M
  #   ports:
  #     - "9092:9092"

volumes:
  pgdata:
YAML

# ── GitHub Actions CI ──
mkdir -p .github/workflows

cat > .github/workflows/ci.yml << 'YAML'
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  hospital-service:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: katixo
          POSTGRES_PASSWORD: test
          POSTGRES_DB: katixo_test
        ports: ["5432:5432"]
        options: --health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build and test hospital-service
        run: |
          cd katixo-hospital-service
          ./mvnw clean verify
        env:
          DB_PASSWORD: test
          JWT_SECRET: test-secret-key-min-32-chars-long
YAML

# ── .gitignore ──
cat > .gitignore << 'EOF'
# Java
target/
*.class
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS
.DS_Store
Thumbs.db

# Env
.env
*.env.local

# Flutter
build/
.dart_tool/
.packages

# Docker
docker-compose.override.yml
EOF

echo ""
echo "✅ Repository scaffold created at: $ROOT/"
echo ""
echo "Directory structure:"
find . -type d | head -60
echo "..."
echo ""
echo "Next: Create Flyway migrations, OpenAPI specs, and design docs"
