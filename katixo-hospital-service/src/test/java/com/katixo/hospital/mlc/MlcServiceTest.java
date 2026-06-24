package com.katixo.hospital.mlc;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MlcServiceTest {

    @Mock MedicoLegalCaseRepository repository;
    @Mock AuditService auditService;

    private MlcService service;

    @BeforeEach
    void setUp() {
        service = new MlcService(repository, auditService);
        TenantContext.set(new TenantContext("demo-tenant", "1", "1", "3", "doctor"));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            MedicoLegalCase c = i.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 42L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registersMlcWithNumber() {
        MedicoLegalCase c = service.register(55L, MedicoLegalCase.MlcType.RTA, null,
                "Police", "City PS", "FIR-9", false, "road traffic accident");
        assertEquals("MLC-42", c.getMlcNumber());
        assertEquals(MedicoLegalCase.CaseStatus.REGISTERED, c.getCaseStatus());
        assertEquals(MedicoLegalCase.MlcType.RTA, c.getMlcType());
    }

    @Test
    void patientRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(
                null, MedicoLegalCase.MlcType.ASSAULT, null, null, null, null, false, null));
        assertEquals("MLC_PATIENT_REQUIRED", ex.getCode());
    }
}
