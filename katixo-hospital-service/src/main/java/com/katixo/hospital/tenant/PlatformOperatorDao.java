package com.katixo.hospital.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Plain-JDBC access to {@code platform.platform_operator}. Like
 * {@link TenantRegistryDao}, this is deliberately NOT a JPA repository: the
 * platform schema is fixed and must be reachable regardless of (and without)
 * any tenant context — platform operators have no tenant.
 */
@Repository
@RequiredArgsConstructor
public class PlatformOperatorDao {

    private static final String TABLE = TenantSchemas.PLATFORM_SCHEMA + ".platform_operator";
    private static final String COLS = "id, username, password_hash, display_name, status";

    private static final RowMapper<PlatformOperator> MAPPER = (rs, i) -> new PlatformOperator(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("status"));

    private final JdbcTemplate jdbcTemplate;

    public Optional<PlatformOperator> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + TABLE + " WHERE username = ?", MAPPER, username)
                .stream().findFirst();
    }

    public List<PlatformOperator> findAll() {
        return jdbcTemplate.query("SELECT " + COLS + " FROM " + TABLE + " ORDER BY username", MAPPER);
    }

    public boolean existsByUsername(String username) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE username = ?", Integer.class, username);
        return n != null && n > 0;
    }

    public void insert(String username, String passwordHash, String displayName) {
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " (username, password_hash, display_name, status)"
                        + " VALUES (?, ?, ?, 'ACTIVE')",
                username, passwordHash, displayName);
    }
}
