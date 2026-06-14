package com.katixo.hospital.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Programmatic Flyway runner. Spring Boot's auto Flyway is disabled because a
 * single static run can't cover N tenant schemas:
 *
 * <ul>
 *   <li>{@code db/migration/platform} → applied once to the {@code platform} schema</li>
 *   <li>{@code db/migration/tenant}   → applied to EVERY tenant schema (and to each
 *       new schema at provisioning time)</li>
 * </ul>
 *
 * Tenant migration SQL must stay schema-agnostic: no CREATE SCHEMA, no
 * SET search_path, no schema-qualified table names — Flyway pins the
 * search_path to the target schema for each run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {

    public static final String PLATFORM_LOCATION = "classpath:db/migration/platform";
    public static final String TENANT_LOCATION = "classpath:db/migration/tenant";

    private final DataSource dataSource;

    public void migratePlatformSchema() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(PLATFORM_LOCATION)
                .schemas(TenantSchemas.PLATFORM_SCHEMA)
                .defaultSchema(TenantSchemas.PLATFORM_SCHEMA)
                .createSchemas(true)
                .load()
                .migrate();
        log.info("Platform schema '{}' migrated", TenantSchemas.PLATFORM_SCHEMA);
    }

    public void migrateTenantSchema(String schemaName, String tenantId) {
        TenantSchemas.requireValid(schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .locations(TENANT_LOCATION)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                // Lets seed migrations stamp rows with the owning tenant
                // (e.g. hospital_policy.tenant_id = '${tenantId}').
                .placeholders(java.util.Map.of("tenantId", tenantId))
                .load()
                .migrate();
        log.info("Tenant schema '{}' migrated", schemaName);
    }
}
