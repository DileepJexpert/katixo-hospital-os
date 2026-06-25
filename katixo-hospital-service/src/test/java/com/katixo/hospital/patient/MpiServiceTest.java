package com.katixo.hospital.patient;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MpiServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock PatientRepository repository;
    @Mock AuditService auditService;

    private MpiService service;

    @BeforeEach
    void setUp() {
        service = new MpiService(repository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "3", "frontdesk"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Patient active(long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setStatus(BaseEntity.EntityStatus.ACTIVE);
        p.setFirstName("Ravi");
        p.setLastName("Kumar");
        return p;
    }

    @Test
    void mergeLinksAndDeactivatesDuplicate() {
        Patient survivor = active(1L);
        Patient duplicate = active(2L);
        when(repository.findByIdAndTenantIdAndBranchId(1L, TENANT, 1L)).thenReturn(Optional.of(survivor));
        when(repository.findByIdAndTenantIdAndBranchId(2L, TENANT, 1L)).thenReturn(Optional.of(duplicate));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Patient result = service.merge(1L, 2L, "same person");

        assertEquals(1L, result.getId());
        assertEquals(BaseEntity.EntityStatus.INACTIVE, duplicate.getStatus());
        assertEquals(1L, duplicate.getMergedIntoId());
    }

    @Test
    void cannotMergeIntoSelf() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.merge(1L, 1L, "x"));
        assertEquals("MPI_SAME_PATIENT", ex.getCode());
    }

    @Test
    void cannotMergeAlreadyMergedDuplicate() {
        Patient survivor = active(1L);
        Patient duplicate = active(2L);
        duplicate.setMergedIntoId(9L);
        when(repository.findByIdAndTenantIdAndBranchId(1L, TENANT, 1L)).thenReturn(Optional.of(survivor));
        when(repository.findByIdAndTenantIdAndBranchId(2L, TENANT, 1L)).thenReturn(Optional.of(duplicate));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.merge(1L, 2L, "x"));
        assertEquals("MPI_ALREADY_MERGED", ex.getCode());
    }
}
