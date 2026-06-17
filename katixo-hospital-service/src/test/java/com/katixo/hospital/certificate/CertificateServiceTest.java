package com.katixo.hospital.certificate;

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

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock CertificateTemplateRepository templateRepository;
    @Mock CertificateRepository certificateRepository;
    @Mock AuditService auditService;

    private CertificateService service;

    @BeforeEach
    void setUp() {
        service = new CertificateService(templateRepository, certificateRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(certificateRepository.nextCertificateSequence()).thenReturn(1001L);
        lenient().when(templateRepository.save(any())).thenAnswer(inv -> {
            CertificateTemplate t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", 3L);
            return t;
        });
        lenient().when(certificateRepository.save(any())).thenAnswer(inv -> {
            Certificate c = inv.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 7L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createTemplateRejectsDuplicateCode() {
        when(templateRepository.existsByTenantIdAndBranchIdAndCode(TENANT, 1L, "FIT"))
                .thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createTemplate("FIT", "Fitness certificate",
                        CertificateTemplate.CertificateType.FITNESS, "Certified fit...", "ENGLISH"));
        assertEquals("CERT_CODE_EXISTS", ex.getCode());
    }

    @Test
    void issueFromTemplateSnapshotsTitleAndBody() {
        CertificateTemplate t = new CertificateTemplate();
        t.setCertificateType(CertificateTemplate.CertificateType.FITNESS);
        t.setTitle("Fitness certificate");
        t.setBodyText("This is to certify that the patient is medically fit.");
        ReflectionTestUtils.setField(t, "id", 3L);
        when(templateRepository.findByIdAndTenantIdAndBranchId(3L, TENANT, 1L)).thenReturn(Optional.of(t));

        Certificate c = service.issue(55L, 3L, null, null, null,
                42L, "Asha Rao", LocalDate.of(2026, 6, 17), null, null, "For employer");

        assertEquals("CERT-1001", c.getCertificateNumber());
        assertEquals("Fitness certificate", c.getTitle());
        assertEquals("This is to certify that the patient is medically fit.", c.getBodyText());
        assertEquals(CertificateTemplate.CertificateType.FITNESS, c.getCertificateType());
        assertEquals(Certificate.CertificateStatus.ISSUED, c.getCertificateStatus());
    }

    @Test
    void issueFreeFormRequiresTitleAndBody() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.issue(55L, null, CertificateTemplate.CertificateType.SICKNESS,
                        "  ", "  ", null, null, null, null, null, null));
        assertEquals("CERT_TITLE_REQUIRED", ex.getCode());
    }

    @Test
    void issueRejectsBackwardsValidity() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.issue(55L, null, CertificateTemplate.CertificateType.SICKNESS,
                        "Sick leave", "Rest advised", null, null, null,
                        LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 15), null));
        assertEquals("CERT_INVALID_VALIDITY", ex.getCode());
    }

    @Test
    void issueDefaultsIssueDateToToday() {
        Certificate c = service.issue(55L, null, CertificateTemplate.CertificateType.MEDICAL,
                "Medical certificate", "Diagnosis recorded.", null, null, null, null, null, null);
        assertEquals(LocalDate.now(), c.getIssueDate());
    }

    @Test
    void revokeSetsRevokedStateAndReason() {
        Certificate issued = new Certificate();
        issued.setCertificateStatus(Certificate.CertificateStatus.ISSUED);
        issued.setCertificateNumber("CERT-1001");
        ReflectionTestUtils.setField(issued, "id", 7L);
        when(certificateRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(issued));

        Certificate c = service.revoke(7L, "Issued in error");
        assertEquals(Certificate.CertificateStatus.REVOKED, c.getCertificateStatus());
        assertEquals("Issued in error", c.getRevokedReason());
        assertNotNull(c.getRevokedAt());
    }

    @Test
    void revokeRejectsAlreadyRevoked() {
        Certificate revoked = new Certificate();
        revoked.setCertificateStatus(Certificate.CertificateStatus.REVOKED);
        revoked.setCertificateNumber("CERT-1001");
        ReflectionTestUtils.setField(revoked, "id", 7L);
        when(certificateRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(revoked));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.revoke(7L, "again"));
        assertEquals("CERT_ALREADY_REVOKED", ex.getCode());
    }
}
