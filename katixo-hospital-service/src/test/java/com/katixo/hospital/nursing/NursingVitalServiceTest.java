package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NursingVitalServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock NursingVitalRepository vitalRepository;
    @Mock AuditService auditService;

    private NursingVitalService service;

    @BeforeEach
    void setUp() {
        service = new NursingVitalService(vitalRepository, auditService);
        // tenantId, hospitalGroupId, branchId, userId, username
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "nurse.asha"));
        lenient().when(vitalRepository.save(any())).thenAnswer(inv -> {
            NursingVital v = inv.getArgument(0);
            if (v.getId() == null) ReflectionTestUtils.setField(v, "id", 7L);
            return v;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordWithOneVitalSucceedsAndStampsRecordedByName() {
        NursingVital v = service.record(55L, 100L, null,
                null, 78, null, null, null, null, null, null, null, "stable");

        assertEquals(55L, v.getPatientId());
        assertEquals(100L, v.getAdmissionId());
        assertEquals(78, v.getPulseBpm());
        assertEquals("nurse.asha", v.getRecordedByName());
        assertEquals(BaseEntity.EntityStatus.ACTIVE, v.getStatus());
    }

    @Test
    void recordWithNoVitalsThrowsVitalEmpty() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record(55L, null, null,
                        null, null, null, null, null, null, null, null, null, "no values"));
        assertEquals("VITAL_EMPTY", ex.getCode());
    }

    @Test
    void recordWithPainScoreElevenThrowsPainRange() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record(55L, null, null,
                        null, null, null, null, null, null, null, null, 11, null));
        assertEquals("VITAL_PAIN_RANGE", ex.getCode());
    }

    @Test
    void recordWithSpo2OneOhFiveThrowsSpo2Range() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record(55L, null, null,
                        null, null, null, null, null, 105, null, null, null, null));
        assertEquals("VITAL_SPO2_RANGE", ex.getCode());
    }

    @Test
    void recordMissingPatientThrowsPatientRequired() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record(null, null, null,
                        new BigDecimal("37.2"), null, null, null, null, null, null, null, null, null));
        assertEquals("VITAL_PATIENT_REQUIRED", ex.getCode());
    }

    @Test
    void listByAdmissionUsesAdmissionFinder() {
        when(vitalRepository.findByTenantIdAndBranchIdAndAdmissionIdOrderByRecordedAtDesc(
                eq(TENANT), eq(1L), eq(100L), any(Pageable.class))).thenReturn(List.of());

        service.list(55L, 100L, 50);

        verify(vitalRepository).findByTenantIdAndBranchIdAndAdmissionIdOrderByRecordedAtDesc(
                eq(TENANT), eq(1L), eq(100L), any(Pageable.class));
        verify(vitalRepository, never()).findByTenantIdAndBranchIdAndPatientIdOrderByRecordedAtDesc(
                any(), any(), any(), any());
    }

    @Test
    void deleteSoftDeletesRecord() {
        NursingVital existing = new NursingVital();
        existing.setPatientId(55L);
        existing.setStatus(BaseEntity.EntityStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(vitalRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L))
                .thenReturn(Optional.of(existing));

        service.delete(7L);

        ArgumentCaptor<NursingVital> captor = ArgumentCaptor.forClass(NursingVital.class);
        verify(vitalRepository).save(captor.capture());
        assertEquals(BaseEntity.EntityStatus.DELETED, captor.getValue().getStatus());
    }

    @Test
    void getCrossTenantReturnsNotFound() {
        when(vitalRepository.findByIdAndTenantIdAndBranchId(99L, TENANT, 1L))
                .thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.get(99L));
        assertEquals("VITAL_NOT_FOUND", ex.getCode());
    }
}
