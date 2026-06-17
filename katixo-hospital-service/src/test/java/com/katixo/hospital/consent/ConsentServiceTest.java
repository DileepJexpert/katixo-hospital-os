package com.katixo.hospital.consent;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock ConsentTemplateRepository templateRepository;
    @Mock ConsentRecordRepository recordRepository;
    @Mock AuditService auditService;

    private ConsentService service;

    @BeforeEach
    void setUp() {
        service = new ConsentService(templateRepository, recordRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(recordRepository.nextConsentSequence()).thenReturn(1001L);
        lenient().when(templateRepository.save(any())).thenAnswer(inv -> {
            ConsentTemplate t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", 3L);
            return t;
        });
        lenient().when(recordRepository.save(any())).thenAnswer(inv -> {
            ConsentRecord r = inv.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 7L);
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createTemplateRejectsDuplicateCode() {
        when(templateRepository.existsByTenantIdAndBranchIdAndCode(TENANT, 1L, "SURG"))
                .thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createTemplate("SURG", "Surgery consent",
                        ConsentTemplate.ConsentType.SURGERY, "I consent to the surgery...", "ENGLISH"));
        assertEquals("CONSENT_CODE_EXISTS", ex.getCode());
    }

    @Test
    void captureFromTemplateSnapshotsTitleAndBody() {
        ConsentTemplate t = new ConsentTemplate();
        t.setConsentType(ConsentTemplate.ConsentType.SURGERY);
        t.setTitle("Surgery consent");
        t.setBodyText("I consent to the surgical procedure.");
        t.setLanguage("ENGLISH");
        ReflectionTestUtils.setField(t, "id", 3L);
        when(templateRepository.findByIdAndTenantIdAndBranchId(3L, TENANT, 1L)).thenReturn(Optional.of(t));

        ConsentRecord r = service.capture(55L, 3L, null, null, null,
                ConsentRecord.SourceType.OT_BOOKING, 88L,
                ConsentRecord.Signatory.PATIENT, "Ramesh Kumar", null, "Sister on duty", null, null);

        assertEquals("CONS-1001", r.getRecordNumber());
        assertEquals("Surgery consent", r.getTitle());
        assertEquals("I consent to the surgical procedure.", r.getBodyText());
        assertEquals(ConsentTemplate.ConsentType.SURGERY, r.getConsentType());
        assertEquals("ENGLISH", r.getLanguage()); // inherited from template
        assertEquals(ConsentRecord.ConsentStatus.GIVEN, r.getConsentStatus());
    }

    @Test
    void captureRequiresRelationWhenSignatoryNotPatient() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.capture(55L, null, ConsentTemplate.ConsentType.PROCEDURE,
                        "Procedure", "Body text", null, null,
                        ConsentRecord.Signatory.GUARDIAN, "Sita Devi", "  ", null, null, null));
        assertEquals("CONSENT_RELATION_REQUIRED", ex.getCode());
    }

    @Test
    void captureFreeFormRequiresTitleAndBody() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.capture(55L, null, ConsentTemplate.ConsentType.GENERAL,
                        "  ", "  ", null, null,
                        ConsentRecord.Signatory.PATIENT, "Ramesh", null, null, null, null));
        assertEquals("CONSENT_TITLE_REQUIRED", ex.getCode());
    }

    @Test
    void captureRefusedKeepsRefusedStatus() {
        ConsentRecord r = service.capture(55L, null, ConsentTemplate.ConsentType.BLOOD_TRANSFUSION,
                "Transfusion", "I decline the transfusion.", ConsentRecord.SourceType.IPD_ADMISSION, 4L,
                ConsentRecord.Signatory.PATIENT, "Ramesh", null, null, null,
                ConsentRecord.ConsentStatus.REFUSED);
        assertEquals(ConsentRecord.ConsentStatus.REFUSED, r.getConsentStatus());
    }

    @Test
    void withdrawOnlyAppliesToGivenConsent() {
        ConsentRecord refused = new ConsentRecord();
        refused.setConsentStatus(ConsentRecord.ConsentStatus.REFUSED);
        ReflectionTestUtils.setField(refused, "id", 7L);
        when(recordRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(refused));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.withdraw(7L, "changed mind"));
        assertEquals("CONSENT_NOT_WITHDRAWABLE", ex.getCode());
    }

    @Test
    void withdrawSetsWithdrawnStateAndReason() {
        ConsentRecord given = new ConsentRecord();
        given.setConsentStatus(ConsentRecord.ConsentStatus.GIVEN);
        given.setRecordNumber("CONS-1001");
        ReflectionTestUtils.setField(given, "id", 7L);
        when(recordRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(given));

        ConsentRecord r = service.withdraw(7L, "Patient revoked before procedure");
        assertEquals(ConsentRecord.ConsentStatus.WITHDRAWN, r.getConsentStatus());
        assertEquals("Patient revoked before procedure", r.getWithdrawnReason());
        assertNotNull(r.getWithdrawnAt());
    }
}
