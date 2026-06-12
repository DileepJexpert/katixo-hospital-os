package com.katixo.hospital.tenant;

import com.katixo.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, TTL-cached view of the tenant registry. Sits on the hot path —
 * the Hibernate tenant resolver consults it on every request — so registry
 * rows are cached for a short window and invalidated on provisioning changes.
 */
@Component
@RequiredArgsConstructor
public class TenantDirectory {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record CachedTenant(TenantRecord record, Instant loadedAt) {
        boolean isFresh() {
            return loadedAt.plus(TTL).isAfter(Instant.now());
        }
    }

    private final TenantRegistryDao registryDao;
    private final Map<String, CachedTenant> cache = new ConcurrentHashMap<>();

    /** Resolves the schema for a tenant, failing loudly for unknown/suspended tenants. */
    public String schemaFor(String tenantId) {
        return requireActive(tenantId).schemaName();
    }

    public TenantRecord requireActive(String tenantId) {
        TenantRecord record = find(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND",
                        "Tenant is not registered: " + tenantId));
        if (!record.isActive()) {
            throw new BusinessException("TENANT_NOT_ACTIVE",
                    "Tenant '" + tenantId + "' is " + record.status());
        }
        return record;
    }

    public Optional<TenantRecord> find(String tenantId) {
        CachedTenant cached = cache.get(tenantId);
        if (cached != null && cached.isFresh()) {
            return Optional.of(cached.record());
        }
        Optional<TenantRecord> loaded = registryDao.findByTenantId(tenantId);
        loaded.ifPresent(r -> cache.put(tenantId, new CachedTenant(r, Instant.now())));
        if (loaded.isEmpty()) {
            cache.remove(tenantId);
        }
        return loaded;
    }

    public void invalidate(String tenantId) {
        cache.remove(tenantId);
    }

    public void invalidateAll() {
        cache.clear();
    }
}
