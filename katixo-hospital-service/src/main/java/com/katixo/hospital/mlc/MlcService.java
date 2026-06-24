package com.katixo.hospital.mlc;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Medico-legal case (MLC) register — register, list, and close cases. */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MlcService {

    private final MedicoLegalCaseRepository repository;
    private final AuditService auditService;

    public MedicoLegalCase register(Long patientId, MedicoLegalCase.MlcType type, LocalDateTime incidentAt,
                                    String broughtBy, String policeStation, String firNumber,
                                    boolean broughtDead, String remarks) {
        if (patientId == null) {
            throw new BusinessException("MLC_PATIENT_REQUIRED", "patientId is required");
        }
        if (type == null) {
            throw new BusinessException("MLC_TYPE_REQUIRED", "MLC type is required");
        }
        MedicoLegalCase c = new MedicoLegalCase();
        c.setPatientId(patientId);
        c.setMlcType(type);
        c.setIncidentAt(incidentAt);
        c.setBroughtBy(broughtBy);
        c.setPoliceStation(policeStation);
        c.setFirNumber(firNumber);
        c.setBroughtDead(broughtDead);
        c.setCaseStatus(MedicoLegalCase.CaseStatus.REGISTERED);
        c.setRegisteredByDoctorId(userId());
        c.setRemarks(remarks);
        stamp(c);
        MedicoLegalCase saved = repository.save(c);
        saved.setMlcNumber("MLC-" + saved.getId());
        saved = repository.save(saved);

        auditService.audit("MedicoLegalCase", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("mlcNumber", saved.getMlcNumber(), "type", type.name(), "patientId", patientId),
                UUID.randomUUID().toString());
        log.info("MLC {} registered for patient {} ({})", saved.getMlcNumber(), patientId, type);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MedicoLegalCase> list(Long patientId, MedicoLegalCase.CaseStatus status) {
        var ctx = TenantContext.get();
        return repository.search(ctx.getTenantId(), branchId(), patientId, status);
    }

    @Transactional(readOnly = true)
    public MedicoLegalCase get(Long id) {
        var ctx = TenantContext.get();
        return repository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("MLC_NOT_FOUND", "MLC not found: " + id));
    }

    public MedicoLegalCase close(Long id, String remarks) {
        MedicoLegalCase c = get(id);
        c.setCaseStatus(MedicoLegalCase.CaseStatus.CLOSED);
        if (remarks != null && !remarks.isBlank()) {
            c.setRemarks((c.getRemarks() == null ? "" : c.getRemarks() + " | ") + remarks);
        }
        c.setUpdatedBy(userId());
        c.setUpdatedAt(LocalDateTime.now());
        MedicoLegalCase saved = repository.save(c);
        auditService.audit("MedicoLegalCase", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("mlcNumber", saved.getMlcNumber(), "status", "CLOSED"), UUID.randomUUID().toString());
        return saved;
    }

    private void stamp(MedicoLegalCase c) {
        var ctx = TenantContext.get();
        c.setTenantId(ctx.getTenantId());
        c.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        c.setBranchId(branchId());
        c.setCreatedBy(userId());
        c.setUpdatedBy(userId());
        c.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
