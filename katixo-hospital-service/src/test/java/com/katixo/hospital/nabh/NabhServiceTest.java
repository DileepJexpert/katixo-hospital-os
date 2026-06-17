package com.katixo.hospital.nabh;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NabhServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock QualityIndicatorRepository indicatorRepository;
    @Mock QualityIndicatorReadingRepository readingRepository;
    @Mock IncidentReportRepository incidentRepository;
    @Mock AuditService auditService;

    private NabhService service;

    @BeforeEach
    void setUp() {
        service = new NabhService(indicatorRepository, readingRepository, incidentRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(incidentRepository.nextIncidentSequence()).thenReturn(1001L);
        lenient().when(indicatorRepository.save(any())).thenAnswer(inv -> {
            QualityIndicator q = inv.getArgument(0);
            if (q.getId() == null) ReflectionTestUtils.setField(q, "id", 2L);
            return q;
        });
        lenient().when(incidentRepository.save(any())).thenAnswer(inv -> {
            IncidentReport i = inv.getArgument(0);
            if (i.getId() == null) ReflectionTestUtils.setField(i, "id", 7L);
            return i;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createIndicatorRejectsDuplicateCode() {
        when(indicatorRepository.existsByTenantIdAndBranchIdAndCode(TENANT, 1L, "HAI"))
                .thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createIndicator("HAI", "HAI rate", "Infection", "%", new BigDecimal("2")));
        assertEquals("QI_CODE_EXISTS", ex.getCode());
    }

    @Test
    void reportIncidentCreatesReported() {
        IncidentReport i = service.reportIncident(LocalDate.of(2026, 6, 10),
                IncidentReport.IncidentType.PATIENT_FALL, IncidentReport.Severity.MODERATE,
                "Ward 2", 55L, "Patient slipped near the bed", "Assessed, no fracture");
        assertEquals("INC-1001", i.getReportNumber());
        assertEquals(IncidentReport.IncidentStatus.REPORTED, i.getIncidentStatus());
    }

    @Test
    void incidentLifecycleReviewThenClose() {
        IncidentReport i = new IncidentReport();
        i.setIncidentStatus(IncidentReport.IncidentStatus.REPORTED);
        ReflectionTestUtils.setField(i, "id", 7L);
        when(incidentRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(i));

        assertEquals(IncidentReport.IncidentStatus.UNDER_REVIEW, service.startReview(7L).getIncidentStatus());
        IncidentReport closed = service.closeIncident(7L, "Wet floor, no signage", "Added signage + hourly mopping log");
        assertEquals(IncidentReport.IncidentStatus.CLOSED, closed.getIncidentStatus());
        assertEquals("Wet floor, no signage", closed.getRootCause());
    }

    @Test
    void closeRequiresRootCauseAndAction() {
        IncidentReport i = new IncidentReport();
        i.setIncidentStatus(IncidentReport.IncidentStatus.UNDER_REVIEW);
        ReflectionTestUtils.setField(i, "id", 7L);
        when(incidentRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(i));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.closeIncident(7L, "cause", "  "));
        assertEquals("INCIDENT_CLOSURE_INCOMPLETE", ex.getCode());
    }

    @Test
    void recordReadingNeedsValidIndicator() {
        when(indicatorRepository.findByIdAndTenantIdAndBranchId(99L, TENANT, 1L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordReading(99L, "2026-06", new BigDecimal("1.2"), null, null, null));
        assertEquals("QI_NOT_FOUND", ex.getCode());
    }
}
