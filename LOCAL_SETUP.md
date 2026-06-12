# Local Development Setup — Katixo Hospital OS

## Prerequisites

- Java 21 (LTS)
- Maven 3.8+
- Docker & Docker Compose
- Git

## Quick Start

### 1. Clone Repository
```bash
git clone https://github.com/DileepJexpert/katixo-hospital-os.git
cd katixo-hospital-os
```

### 2. Start Infrastructure (Docker Compose)
```bash
# Start all services
docker-compose up -d

# Check service health
docker-compose ps

# View logs
docker-compose logs -f postgres redis elasticsearch redpanda pgadmin
```

Services:
- **PostgreSQL 17** — http://localhost:5432 (user: `katixo`, password: `katixo_dev_password`)
- **Redis 7** — http://localhost:6379
- **Elasticsearch 8** — http://localhost:9200
- **Redpanda (Kafka)** — http://localhost:9092
- **PgAdmin** — http://localhost:5050 (admin@katixo.local / admin)

### 3. Configure Environment
```bash
cp .env.example .env
# Edit .env with your values (defaults are fine for local dev)
```

### 4. Build Project
```bash
mvn clean install -DskipTests
```

### 5. Run Hospital Service
```bash
cd katixo-hospital-service
mvn spring-boot:run
```

Service will start on **http://localhost:8081**

### 6. Access API & Docs
- **Swagger UI** — http://localhost:8081/api/swagger-ui.html
- **OpenAPI Spec** — http://localhost:8081/api/v1/api-docs

## Database

### Apply Migrations
Migrations are automatically applied on startup (Flyway).

Schemas (schema-per-tenant SaaS isolation):
- `platform` — control plane: `tenant_registry` (tenant → schema + per-tenant ERP credentials)
- `t_<tenant_id>` — one schema per hospital tenant with ALL business tables (incl. `audit_log`).
  Dev auto-provisions `t_demo_tenant` on startup.

### Sample Database Queries
```sql
-- Connect to postgres
psql -h localhost -U katixo -d katixo_hospital

-- Check schemas
\dn

-- Registered tenants
SELECT tenant_id, schema_name, status FROM platform.tenant_registry;

-- List a tenant's tables
\dt t_demo_tenant.*
```

## Development Workflow

### 1. Create Feature Branch
```bash
git checkout -b feature/your-feature
```

### 2. Make Changes
- Add entity/service/controller
- Write tests
- Follow CLAUDE.md architecture rules

### 3. Test Locally
```bash
# Run unit + integration tests
mvn test

# Run specific test class
mvn test -Dtest=PatientServiceTest
```

### 4. Commit & Push
```bash
git add .
git commit -m "Add feature description"
git push origin feature/your-feature
```

### 5. Create Pull Request
Push branch to GitHub and create PR for review.

## Key Architecture Rules (from CLAUDE.md)

✅ **NEVER violate these:**

1. **Tenant Isolation** — Every query must filter by `tenant_id` + `branch_id` from JWT context
2. **Audit Trail** — Every clinical/financial change logged to `audit_log` table
3. **Policy Engine** — No hardcoded if-else; use `hospital_policy` table + `PolicyService`
4. **Outbox Pattern** — Events written to `outbox_event` in same transaction as business data
5. **Idempotency** — Command APIs (POST/PUT/DELETE) accept `Idempotency-Key` header
6. **ERP Headers** — Every ERP API call includes standard headers (X-Tenant-Id, X-Correlation-Id, etc.)

## Troubleshooting

### PostgreSQL connection refused
```bash
# Check if container is running
docker ps | grep postgres

# View logs
docker logs katixo-postgres

# Restart
docker-compose restart postgres
```

### Redis connection refused
```bash
# Check Redis container
docker logs katixo-redis

# Test connection
redis-cli -h localhost ping
```

### Elasticsearch not responding
```bash
# Check Elasticsearch logs
docker logs katixo-elasticsearch

# Verify cluster health
curl http://localhost:9200/_cluster/health
```

### Application fails to start
```bash
# Check logs
mvn spring-boot:run 2>&1 | tail -50

# Ensure DB migrations ran
psql -h localhost -U katixo -d katixo_hospital -c "SELECT version, description FROM t_demo_tenant.flyway_schema_history;"

# Ensure all environment variables set
cat .env
```

## Useful Commands

### View Service Logs
```bash
docker-compose logs -f postgres        # PostgreSQL
docker-compose logs -f redis           # Redis
docker-compose logs -f elasticsearch   # Elasticsearch
docker-compose logs -f redpanda        # Kafka
```

### Access Database
```bash
# Command line
psql -h localhost -U katixo -d katixo_hospital

# PgAdmin web UI
# http://localhost:5050 → Add Server → Host: postgres, Port: 5432
```

### Clean Up Everything
```bash
# Stop and remove containers, volumes
docker-compose down -v

# Full project rebuild
mvn clean install
```

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=PatientServiceTest
```

### Run Tests with Code Coverage
```bash
mvn test jacoco:report
# Report at: target/site/jacoco/index.html
```

### Integration Tests (TestContainers)
- PostgreSQL container spins up automatically
- Redis container spins up automatically
- Elasticsearch container spins up automatically
- No need to manually start Docker for tests

## IDE Setup (IntelliJ IDEA)

1. **Open Project** — File → Open → select project root
2. **Configure SDK** — File → Project Structure → SDK → Java 21
3. **Enable Annotation Processing** — Settings → Build, Execution → Annotation Processors → Enable
4. **Run Configuration** — Run → Edit Configurations → Add Spring Boot configuration

## CI/CD

GitHub Actions workflow:
- Triggered on push to `main`, `develop`, and feature branches
- Runs: Maven build, unit tests, integration tests, code quality checks
- Builds Docker image on successful test
- Deploys to staging on merge to `develop`

See `.github/workflows/` for details.

## Next Steps

1. ✅ Foundational setup complete (you are here)
2. Create Patient entity & API
3. Create OPD/IPD modules
4. Implement Prescription & Pharmacy flows
5. Build Billing & TPA modules
6. Implement WebSocket for real-time queues
7. Add search with Elasticsearch

See CLAUDE.md for complete module structure and business rules.
