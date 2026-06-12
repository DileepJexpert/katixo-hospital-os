package com.katixo.hospital.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Plain-JDBC access to {@code platform.tenant_registry}. Deliberately NOT a JPA
 * repository: JPA traffic is routed to the current tenant's schema by the
 * multi-tenant connection provider, while the registry must always be read from
 * the fixed platform schema regardless of tenant context.
 */
@Repository
@RequiredArgsConstructor
public class TenantRegistryDao {

    private static final String TABLE = TenantSchemas.PLATFORM_SCHEMA + ".tenant_registry";

    private static final RowMapper<TenantRecord> MAPPER = (rs, i) -> new TenantRecord(
            rs.getString("tenant_id"),
            rs.getString("schema_name"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("erp_base_url"),
            rs.getString("erp_api_key"),
            rs.getString("erp_org_code"));

    private final JdbcTemplate jdbcTemplate;

    public Optional<TenantRecord> findByTenantId(String tenantId) {
        List<TenantRecord> rows = jdbcTemplate.query(
                "SELECT tenant_id, schema_name, display_name, status, erp_base_url, erp_api_key, erp_org_code"
                        + " FROM " + TABLE + " WHERE tenant_id = ?",
                MAPPER, tenantId);
        return rows.stream().findFirst();
    }

    public List<TenantRecord> findAll() {
        return jdbcTemplate.query(
                "SELECT tenant_id, schema_name, display_name, status, erp_base_url, erp_api_key, erp_org_code"
                        + " FROM " + TABLE + " ORDER BY tenant_id",
                MAPPER);
    }

    public void insert(TenantRecord record) {
        jdbcTemplate.update(
                "INSERT INTO " + TABLE
                        + " (tenant_id, schema_name, display_name, status, erp_base_url, erp_api_key, erp_org_code)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                record.tenantId(), record.schemaName(), record.displayName(), record.status(),
                record.erpBaseUrl(), record.erpApiKey(), record.erpOrgCode());
    }

    public void updateStatus(String tenantId, String status) {
        jdbcTemplate.update(
                "UPDATE " + TABLE + " SET status = ?, updated_at = NOW() WHERE tenant_id = ?",
                status, tenantId);
    }

    public void updateErpConfig(String tenantId, String erpBaseUrl, String erpApiKey, String erpOrgCode) {
        jdbcTemplate.update(
                "UPDATE " + TABLE
                        + " SET erp_base_url = ?, erp_api_key = ?, erp_org_code = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ?",
                erpBaseUrl, erpApiKey, erpOrgCode, tenantId);
    }
}
