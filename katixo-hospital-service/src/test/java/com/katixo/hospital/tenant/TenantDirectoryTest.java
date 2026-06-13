package com.katixo.hospital.tenant;

import com.katixo.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantDirectoryTest {

    private TenantRegistryDao registryDao;
    private TenantDirectory directory;

    private static TenantRecord tenant(String status) {
        return new TenantRecord("apollo", "t_apollo", "Apollo", status);
    }

    @BeforeEach
    void setUp() {
        registryDao = mock(TenantRegistryDao.class);
        directory = new TenantDirectory(registryDao);
    }

    @Test
    void resolvesSchemaAndCachesRegistryLookup() {
        when(registryDao.findByTenantId("apollo")).thenReturn(Optional.of(tenant(TenantRecord.STATUS_ACTIVE)));

        assertEquals("t_apollo", directory.schemaFor("apollo"));
        assertEquals("t_apollo", directory.schemaFor("apollo"));

        verify(registryDao, times(1)).findByTenantId("apollo");
    }

    @Test
    void invalidateForcesReload() {
        when(registryDao.findByTenantId("apollo")).thenReturn(Optional.of(tenant(TenantRecord.STATUS_ACTIVE)));

        directory.schemaFor("apollo");
        directory.invalidate("apollo");
        directory.schemaFor("apollo");

        verify(registryDao, times(2)).findByTenantId("apollo");
    }

    @Test
    void unknownTenantFailsLoudly() {
        when(registryDao.findByTenantId(anyString())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> directory.schemaFor("ghost"));
        assertEquals("TENANT_NOT_FOUND", ex.getCode());
    }

    @Test
    void suspendedTenantIsRejected() {
        when(registryDao.findByTenantId("apollo")).thenReturn(Optional.of(tenant(TenantRecord.STATUS_SUSPENDED)));

        BusinessException ex = assertThrows(BusinessException.class, () -> directory.requireActive("apollo"));
        assertEquals("TENANT_NOT_ACTIVE", ex.getCode());
    }
}
