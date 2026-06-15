package com.katixo.hospital.billing;

import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientCreditServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock PatientRepository patientRepository;
    @Mock PatientBillRepository billRepository;

    private PatientCreditService service;

    @BeforeEach
    void setUp() {
        service = new PatientCreditService(patientRepository, billRepository);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void statusBuckets() {
        assertEquals("NO_LIMIT", PatientCreditService.creditStatus(BigDecimal.ZERO, new BigDecimal("5000")));
        assertEquals("OK", PatientCreditService.creditStatus(new BigDecimal("10000"), new BigDecimal("5000")));
        assertEquals("WARN", PatientCreditService.creditStatus(new BigDecimal("10000"), new BigDecimal("8000")));
        assertEquals("BLOCK", PatientCreditService.creditStatus(new BigDecimal("10000"), new BigDecimal("10000")));
        assertEquals("BLOCK", PatientCreditService.creditStatus(new BigDecimal("10000"), new BigDecimal("12000")));
    }

    @Test
    void creditComputesAvailableAndStatus() {
        Patient p = new Patient();
        p.setCreditLimit(new BigDecimal("10000"));
        when(patientRepository.findByIdAndTenantIdAndBranchId(3L, TENANT, 1L)).thenReturn(Optional.of(p));
        when(billRepository.sumOutstandingForPatient(TENANT, 1L, 3L)).thenReturn(new BigDecimal("9000"));

        Map<String, Object> view = service.credit(3L);

        assertEquals(0, new BigDecimal("9000").compareTo((BigDecimal) view.get("outstanding")));
        assertEquals(0, new BigDecimal("1000").compareTo((BigDecimal) view.get("available")));
        assertEquals("WARN", view.get("status"));
    }
}
