package com.katixo.hospital.emar;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmarServiceTest {

    @Mock MedicationAdministrationRepository repository;
    @Mock AuditService auditService;

    private EmarService service;

    @BeforeEach
    void setUp() {
        service = new EmarService(repository, auditService);
        TenantContext.set(new TenantContext("demo-tenant", "1", "1", "4", "nurse"));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            MedicationAdministration m = i.getArgument(0);
            if (m.getId() == null) ReflectionTestUtils.setField(m, "id", 7L);
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordsAdministrationWithRightsConfirmed() {
        MedicationAdministration m = service.record(55L, 5L, null, "AMOX", "Amoxicillin 500mg",
                "500 mg", "Oral", null, MedicationAdministration.AdminStatus.ADMINISTERED, null, true, null);
        assertEquals(MedicationAdministration.AdminStatus.ADMINISTERED, m.getAdminStatus());
        assertTrue(m.isRightsConfirmed());
        assertEquals(4L, m.getAdministeredBy());
    }

    @Test
    void administeringWithoutRightsIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.record(55L, null, null,
                "AMOX", "Amoxicillin", "500 mg", "Oral", null,
                MedicationAdministration.AdminStatus.ADMINISTERED, null, false, null));
        assertEquals("MAR_RIGHTS_REQUIRED", ex.getCode());
    }

    @Test
    void refusalRequiresReason() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.record(55L, null, null,
                "AMOX", "Amoxicillin", "500 mg", "Oral", null,
                MedicationAdministration.AdminStatus.REFUSED, null, false, null));
        assertEquals("MAR_REASON_REQUIRED", ex.getCode());
    }
}
