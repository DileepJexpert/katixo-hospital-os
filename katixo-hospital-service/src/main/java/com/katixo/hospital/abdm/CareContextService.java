package com.katixo.hospital.abdm;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.katixo.hospital.abdm.AbdmDtos.CreateCareContextRequest;

/**
 * Creates ABDM care contexts for completed care episodes (OPD visit / IPD admission).
 *
 * A care context requires an active ABHA link; creation emits a CareContextCreated
 * outbox event that the future integration-service poller turns into a gateway
 * link-care-context call (the gateway then flips linkStatus via callback).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CareContextService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final CareContextRepository careContextRepository;
    private final AbdmService abdmService;
    private final OPDVisitRepository visitRepository;
    private final IPDAdmissionRepository admissionRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    public CareContext createCareContext(CreateCareContextRequest request) {
        var ctx = TenantContext.get();

        Long patientId = resolvePatientId(request.getSourceType(), request.getSourceId());

        // Requires an active ABHA link (also enforces the abdm.enabled policy gate).
        AbhaLink abhaLink = abdmService.getActiveLink(patientId);

        if (careContextRepository.existsByTenantIdAndSourceTypeAndSourceId(
                ctx.getTenantId(), request.getSourceType(), request.getSourceId())) {
            throw new BusinessException("CARE_CONTEXT_EXISTS",
                    "A care context already exists for this " + request.getSourceType());
        }

        CareContext careContext = new CareContext();
        careContext.setPatientId(patientId);
        careContext.setAbhaLinkId(abhaLink.getId());
        careContext.setSourceType(request.getSourceType());
        careContext.setSourceId(request.getSourceId());
        careContext.setCareContextReference(
                CareContext.buildReference(request.getSourceType(), request.getSourceId()));
        careContext.setDisplayName(buildDisplayName(request.getSourceType()));
        careContext.setLinkStatus(CareContext.LinkStatus.PENDING_LINK);
        stamp(careContext);

        CareContext saved = careContextRepository.save(careContext);

        outboxEventService.publish("CareContext", String.valueOf(saved.getId()),
                "CareContextCreated", snapshot(saved));
        auditService.audit("CareContext", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Care context {} created for patient {} ({})",
                saved.getCareContextReference(), patientId, request.getSourceType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CareContext> listForPatient(Long patientId) {
        var ctx = TenantContext.get();
        return careContextRepository.findByTenantIdAndBranchIdAndPatientIdOrderByCreatedAtDesc(
                ctx.getTenantId(), branchId(), patientId);
    }

    // ------------------------------------------------------------

    /** Validates the source episode exists in this tenant/branch and returns its patient. */
    private Long resolvePatientId(CareContext.SourceType sourceType, Long sourceId) {
        var ctx = TenantContext.get();
        return switch (sourceType) {
            case OPD_VISIT -> visitRepository
                    .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId())
                    .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND",
                            "Visit not found: " + sourceId))
                    .getPatientId();
            case IPD_ADMISSION -> admissionRepository
                    .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId())
                    .orElseThrow(() -> new BusinessException("ADMISSION_NOT_FOUND",
                            "Admission not found: " + sourceId))
                    .getPatientId();
        };
    }

    private String buildDisplayName(CareContext.SourceType sourceType) {
        String episode = sourceType == CareContext.SourceType.OPD_VISIT
                ? "OPD consultation" : "Hospital admission";
        return episode + ", " + LocalDate.now().format(DISPLAY_DATE);
    }

    private Map<String, Object> snapshot(CareContext c) {
        return Map.of(
                "id", c.getId(),
                "patientId", c.getPatientId(),
                "abhaLinkId", c.getAbhaLinkId(),
                "careContextReference", c.getCareContextReference(),
                "sourceType", c.getSourceType().name(),
                "sourceId", c.getSourceId(),
                "linkStatus", c.getLinkStatus().name()
        );
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
