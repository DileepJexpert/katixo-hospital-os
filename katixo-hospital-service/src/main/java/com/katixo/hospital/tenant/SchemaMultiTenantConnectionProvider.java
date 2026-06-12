package com.katixo.hospital.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema-per-tenant connection provider: hands Hibernate pooled connections
 * with {@code search_path} pointed at the tenant's schema, and resets the
 * path before the connection goes back to the pool so no tenant ever inherits
 * another tenant's search_path.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String schemaName) throws SQLException {
        Connection connection = dataSource.getConnection();
        setSearchPath(connection, TenantSchemas.requireValid(schemaName));
        return connection;
    }

    @Override
    public void releaseConnection(String schemaName, Connection connection) throws SQLException {
        try {
            setSearchPath(connection, TenantSchemas.PLATFORM_SCHEMA);
        } finally {
            connection.close();
        }
    }

    private void setSearchPath(Connection connection, String schemaName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schemaName + "\"");
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return unwrapType.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
    }
}
