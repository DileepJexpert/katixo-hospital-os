package com.katixo.hospital.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Startup sequence for the multi-tenant database:
 * 1. migrate the platform (control-plane) schema,
 * 2. apply pending migrations to every registered tenant schema,
 * 3. optionally auto-provision the demo tenant for local development.
 *
 * Runs at order 1 so seeders (e.g. DevUserSeeder at order 20) find their
 * tenant schema ready.
 */
@Component
@Order(TenantBootstrap.ORDER)
@RequiredArgsConstructor
@Slf4j
public class TenantBootstrap implements ApplicationRunner {

    public static final int ORDER = 1;

    private final TenantMigrationService migrationService;
    private final TenantProvisioningService provisioningService;
    private final TenantRegistryDao registryDao;

    @Value("${katixo.tenant.demo.enabled:false}")
    private boolean demoTenantEnabled;

    @Value("${katixo.tenant.demo.tenant-id:demo-tenant}")
    private String demoTenantId;

    @Override
    public void run(ApplicationArguments args) {
        migrationService.migratePlatformSchema();
        provisioningService.migrateAllTenants();

        if (demoTenantEnabled && registryDao.findByTenantId(demoTenantId).isEmpty()) {
            provisioningService.provision(demoTenantId, "Demo Hospital");
            log.info("Auto-provisioned demo tenant '{}'", demoTenantId);
        }
    }
}
