package com.katixo.hospital.discharge;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DischargeService {

    private final DischargeSummaryRepository dischargeSummaryRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    public DischargeSummaryResponse createDischargeSummary(CreateDischargeSummaryRequest request) {
        var ctx = TenantContext.get();
        Long userId = Long.parseLong(ctx.getUserId());

        var summary = new DischargeSummary();
        summary.setTenantId(ctx.getTenantId());
        summary.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        summary.setBranchId(Long.parseLong(ctx.getBranchId()));
        summary.setAdmissionId(request.admissionId);
        summary.setPatientId(request.patientId);
        summary.setChiefComplaints(request.chiefComplaints);
        summary.setDiagnosis(request.diagnosis);
        summary.setTreatmentSummary(request.treatmentSummary);
        summary.setProcedures(request.procedures);
        summary.setMedications(request.medications);
        summary.setFollowUpInstructions(request.followUpInstructions);
        summary.setRestrictions(request.restrictions);
        summary.setWarningSymptoms(request.warningSymptoms);
        summary.setDischargeType(DischargeSummary.DischargeType.valueOf(request.dischargeType));
        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.DRAFT);
        summary.setPreparedBy(userId);
        summary.setPreparedAt(LocalDateTime.now());
        summary.setAdditionalNotes(request.additionalNotes);
        summary.setCreatedBy(userId);
        summary.setUpdatedBy(userId);
        summary.setStatus(BaseEntity.EntityStatus.ACTIVE);

        summary = dischargeSummaryRepository.save(summary);

        auditService.audit("DischargeSummary", String.valueOf(summary.getId()),
                AuditLog.AuditAction.CREATE, null,
                Map.of("dischargeStatus", summary.getDischargeStatus().name(),
                        "admissionId", summary.getAdmissionId()),
                UUID.randomUUID().toString());

        outboxEventService.publish("DischargeSummary", String.valueOf(summary.getId()),
                "discharge.summary.created",
                Map.of("dischargeSummaryId", summary.getId(),
                        "admissionId", summary.getAdmissionId(),
                        "patientId", summary.getPatientId()));

        return toResponse(summary);
    }

    public DischargeSummaryResponse updateDischargeSummary(Long summaryId, UpdateDischargeSummaryRequest request) {
        var ctx = TenantContext.get();
        var summary = dischargeSummaryRepository.findByIdAndTenantIdAndBranchId(summaryId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeStatus() != DischargeSummary.DischargeSummaryStatus.DRAFT) {
            throw new BusinessException("CANNOT_EDIT", "Can only edit draft discharge summaries");
        }

        if (request.diagnosis != null) summary.setDiagnosis(request.diagnosis);
        if (request.treatmentSummary != null) summary.setTreatmentSummary(request.treatmentSummary);
        if (request.medications != null) summary.setMedications(request.medications);
        if (request.followUpInstructions != null) summary.setFollowUpInstructions(request.followUpInstructions);

        summary.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        summary = dischargeSummaryRepository.save(summary);

        auditService.audit("DischargeSummary", String.valueOf(summary.getId()),
                AuditLog.AuditAction.UPDATE, null,
                Map.of("dischargeStatus", summary.getDischargeStatus().name()),
                UUID.randomUUID().toString());

        return toResponse(summary);
    }

    public DischargeSummaryResponse submitForApproval(Long summaryId) {
        var ctx = TenantContext.get();
        var summary = dischargeSummaryRepository.findByIdAndTenantIdAndBranchId(summaryId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeStatus() != DischargeSummary.DischargeSummaryStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Only draft summaries can be submitted");
        }

        var beforeStatus = summary.getDischargeStatus().name();
        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.PENDING_APPROVAL);
        summary.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        summary = dischargeSummaryRepository.save(summary);

        auditService.audit("DischargeSummary", String.valueOf(summary.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("dischargeStatus", beforeStatus),
                Map.of("dischargeStatus", summary.getDischargeStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("DischargeSummary", String.valueOf(summary.getId()),
                "discharge.summary.submitted",
                Map.of("dischargeSummaryId", summary.getId(),
                        "admissionId", summary.getAdmissionId()));

        return toResponse(summary);
    }

    public DischargeSummaryResponse approveDischargeSummary(Long summaryId) {
        var ctx = TenantContext.get();
        Long userId = Long.parseLong(ctx.getUserId());
        var summary = dischargeSummaryRepository.findByIdAndTenantIdAndBranchId(summaryId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeStatus() != DischargeSummary.DischargeSummaryStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Only pending summaries can be approved");
        }

        var beforeStatus = summary.getDischargeStatus().name();
        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.APPROVED);
        summary.setApprovedBy(userId);
        summary.setApprovedAt(LocalDateTime.now());
        summary.setUpdatedBy(userId);
        summary = dischargeSummaryRepository.save(summary);

        auditService.audit("DischargeSummary", String.valueOf(summary.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("dischargeStatus", beforeStatus),
                Map.of("dischargeStatus", summary.getDischargeStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("DischargeSummary", String.valueOf(summary.getId()),
                "discharge.summary.approved",
                Map.of("dischargeSummaryId", summary.getId(),
                        "admissionId", summary.getAdmissionId()));

        return toResponse(summary);
    }

    public DischargeSummaryResponse finalizeDischargeSummary(Long summaryId) {
        var ctx = TenantContext.get();
        Long userId = Long.parseLong(ctx.getUserId());
        var summary = dischargeSummaryRepository.findByIdAndTenantIdAndBranchId(summaryId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeStatus() != DischargeSummary.DischargeSummaryStatus.APPROVED) {
            throw new BusinessException("INVALID_STATUS", "Only approved summaries can be finalized");
        }

        var beforeStatus = summary.getDischargeStatus().name();
        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.FINALIZED);
        summary.setFinishedBy(userId);
        summary.setFinishedAt(LocalDateTime.now());
        summary.setUpdatedBy(userId);
        summary = dischargeSummaryRepository.save(summary);

        auditService.audit("DischargeSummary", String.valueOf(summary.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("dischargeStatus", beforeStatus),
                Map.of("dischargeStatus", summary.getDischargeStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("DischargeSummary", String.valueOf(summary.getId()),
                "discharge.summary.finalized",
                Map.of("dischargeSummaryId", summary.getId(),
                        "admissionId", summary.getAdmissionId()));

        return toResponse(summary);
    }

    public DischargeSummaryResponse getDischargeSummaryByAdmission(Long admissionId) {
        var ctx = TenantContext.get();
        var summary = dischargeSummaryRepository.findByTenantIdAndBranchIdAndAdmissionId(
                        ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), admissionId)
                .orElseThrow(() -> new BusinessException("DISCHARGE_SUMMARY_NOT_FOUND", "No discharge summary for this admission"));
        return toResponse(summary);
    }

    public DischargeSummaryResponse getDischargeSummaryById(Long summaryId) {
        var ctx = TenantContext.get();
        var summary = dischargeSummaryRepository.findByIdAndTenantIdAndBranchId(summaryId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));
        return toResponse(summary);
    }

    public List<DischargeSummaryResponse> getPendingApprovalSummaries() {
        var ctx = TenantContext.get();
        var summaries = dischargeSummaryRepository.findByTenantIdAndBranchIdAndDischargeStatusAndApprovedByIsNull(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                DischargeSummary.DischargeSummaryStatus.PENDING_APPROVAL
        );
        return summaries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private DischargeSummaryResponse toResponse(DischargeSummary summary) {
        return DischargeSummaryResponse.builder()
                .id(summary.getId())
                .admissionId(summary.getAdmissionId())
                .patientId(summary.getPatientId())
                .chiefComplaints(summary.getChiefComplaints())
                .diagnosis(summary.getDiagnosis())
                .treatmentSummary(summary.getTreatmentSummary())
                .procedures(summary.getProcedures())
                .medications(summary.getMedications())
                .followUpInstructions(summary.getFollowUpInstructions())
                .restrictions(summary.getRestrictions())
                .warningSymptoms(summary.getWarningSymptoms())
                .dischargeType(summary.getDischargeType().toString())
                .dischargeStatus(summary.getDischargeStatus().toString())
                .preparedBy(summary.getPreparedBy())
                .preparedAt(summary.getPreparedAt())
                .approvedBy(summary.getApprovedBy())
                .approvedAt(summary.getApprovedAt())
                .finishedBy(summary.getFinishedBy())
                .finishedAt(summary.getFinishedAt())
                .fileUrl(summary.getFileUrl())
                .additionalNotes(summary.getAdditionalNotes())
                .createdAt(summary.getCreatedAt())
                .build();
    }
}
