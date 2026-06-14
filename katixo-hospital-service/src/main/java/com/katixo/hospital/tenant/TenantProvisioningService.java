package com.katixo.hospital.tenant;

import com.katixo.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Creates and manages tenants (one per hospital client). Provisioning =
 * registry row + dedicated schema + full migration run. Designed to be safe to
 * re-run: an interrupted provision (row exists, schema half-migrated) is
 * completed by calling provision again or by the startup migration sweep.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private final TenantRegistryDao registryDao;
    private final TenantMigrationService migrationService;
    private final TenantDirectory tenantDirectory;

    public TenantRecord provision(String tenantId, String displayName) {
        String schemaName = TenantSchemas.schemaNameFor(tenantId);

        TenantRecord existing = registryDao.findByTenantId(tenantId).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new BusinessException("TENANT_ALREADY_EXISTS",
                    "Tenant '" + tenantId + "' is already provisioned");
        }

        if (existing == null) {
            registryDao.insert(new TenantRecord(tenantId, schemaName, displayName,
                    TenantRecord.STATUS_PROVISIONING));
        }

        // Creates the schema if missing and applies all tenant migrations.
        migrationService.migrateTenantSchema(schemaName, tenantId);

        registryDao.updateStatus(tenantId, TenantRecord.STATUS_ACTIVE);
        tenantDirectory.invalidate(tenantId);

        log.info("Tenant '{}' provisioned in schema '{}'", tenantId, schemaName);
        return registryDao.findByTenantId(tenantId).orElseThrow();
    }

    /** Applies pending tenant migrations to every registered tenant schema. */
    public void migrateAllTenants() {
        for (TenantRecord tenant : registryDao.findAll()) {
            try {
                migrationService.migrateTenantSchema(tenant.schemaName(), tenant.tenantId());
            } catch (Exception e) {
                // One broken tenant must not block the rest of the fleet.
                log.error("Migration failed for tenant '{}' (schema '{}'): {}",
                        tenant.tenantId(), tenant.schemaName(), e.getMessage());
            }
        }
        tenantDirectory.invalidateAll();
    }

    public List<TenantRecord> listTenants() {
        return registryDao.findAll();
    }

    public void suspend(String tenantId) {
        registryDao.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found: " + tenantId));
        registryDao.updateStatus(tenantId, TenantRecord.STATUS_SUSPENDED);
        tenantDirectory.invalidate(tenantId);
    }

    public void activate(String tenantId) {
        registryDao.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found: " + tenantId));
        registryDao.updateStatus(tenantId, TenantRecord.STATUS_ACTIVE);
        tenantDirectory.invalidate(tenantId);
    }

}
