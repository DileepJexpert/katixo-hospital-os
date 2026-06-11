package com.katixo.hospital.discharge;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.ApiException;
import com.katixo.hospital.outbox.OutboxEvent;
import com.katixo.hospital.outbox.OutboxPublisher;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DischargeService {

    private final DischargeSummaryRepository dischargeSummaryRepository;
    private final AuditService auditService;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    public DischargeSummaryResponse createDischargeSummary(CreateDischargeSummaryRequest request) {
        var ctx = tenantContext.current();

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
        summary.setPreparedBy(ctx.getCurrentUserId());
        summary.setPreparedAt(LocalDateTime.now());
        summary.setAdditionalNotes(request.additionalNotes);
        summary.setCreatedBy(ctx.getCurrentUserId());
        summary.setUpdatedBy(ctx.getCurrentUserId());

        summary = dischargeSummaryRepository.save(summary);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("CREATE_DISCHARGE_SUMMARY")
                .entityType("DischargeSummary")
                .entityId(summary.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "discharge.summary.created",
                "DischargeSummary",
                summary.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(summary);
    }

    public DischargeSummaryResponse updateDischargeSummary(Long summaryId, UpdateDischargeSummaryRequest request) {
        var ctx = tenantContext.current();
        var summary = dischargeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ApiException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeSummaryStatus() != DischargeSummary.DischargeSummaryStatus.DRAFT) {
            throw new ApiException("CANNOT_EDIT", "Can only edit draft discharge summaries");
        }

        if (request.diagnosis != null) summary.setDiagnosis(request.diagnosis);
        if (request.treatmentSummary != null) summary.setTreatmentSummary(request.treatmentSummary);
        if (request.medications != null) summary.setMedications(request.medications);
        if (request.followUpInstructions != null) summary.setFollowUpInstructions(request.followUpInstructions);

        summary.setUpdatedBy(ctx.getCurrentUserId());
        summary = dischargeSummaryRepository.save(summary);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("UPDATE_DISCHARGE_SUMMARY")
                .entityType("DischargeSummary")
                .entityId(summary.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        return toResponse(summary);
    }

    public DischargeSummaryResponse submitForApproval(Long summaryId) {
        var ctx = tenantContext.current();
        var summary = dischargeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ApiException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeSummaryStatus() != DischargeSummary.DischargeSummaryStatus.DRAFT) {
            throw new ApiException("INVALID_STATUS", "Only draft summaries can be submitted");
        }

        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.PENDING_APPROVAL);
        summary.setUpdatedBy(ctx.getCurrentUserId());
        summary = dischargeSummaryRepository.save(summary);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("SUBMIT_DISCHARGE_SUMMARY")
                .entityType("DischargeSummary")
                .entityId(summary.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "discharge.summary.submitted",
                "DischargeSummary",
                summary.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(summary);
    }

    public DischargeSummaryResponse approveDischargeSummary(Long summaryId) {
        var ctx = tenantContext.current();
        var summary = dischargeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ApiException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeSummaryStatus() != DischargeSummary.DischargeSummaryStatus.PENDING_APPROVAL) {
            throw new ApiException("INVALID_STATUS", "Only pending summaries can be approved");
        }

        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.APPROVED);
        summary.setApprovedBy(ctx.getCurrentUserId());
        summary.setApprovedAt(LocalDateTime.now());
        summary.setUpdatedBy(ctx.getCurrentUserId());
        summary = dischargeSummaryRepository.save(summary);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("APPROVE_DISCHARGE_SUMMARY")
                .entityType("DischargeSummary")
                .entityId(summary.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "discharge.summary.approved",
                "DischargeSummary",
                summary.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(summary);
    }

    public DischargeSummaryResponse finalizeDischargeSummary(Long summaryId) {
        var ctx = tenantContext.current();
        var summary = dischargeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ApiException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));

        if (summary.getDischargeSummaryStatus() != DischargeSummary.DischargeSummaryStatus.APPROVED) {
            throw new ApiException("INVALID_STATUS", "Only approved summaries can be finalized");
        }

        summary.setDischargeStatus(DischargeSummary.DischargeSummaryStatus.FINALIZED);
        summary.setFinishedBy(ctx.getCurrentUserId());
        summary.setFinishedAt(LocalDateTime.now());
        summary.setUpdatedBy(ctx.getCurrentUserId());
        summary = dischargeSummaryRepository.save(summary);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("FINALIZE_DISCHARGE_SUMMARY")
                .entityType("DischargeSummary")
                .entityId(summary.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "discharge.summary.finalized",
                "DischargeSummary",
                summary.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(summary);
    }

    public DischargeSummaryResponse getDischargeSummaryByAdmission(Long admissionId) {
        var summary = dischargeSummaryRepository.findByAdmissionId(admissionId)
                .orElseThrow(() -> new ApiException("DISCHARGE_SUMMARY_NOT_FOUND", "No discharge summary for this admission"));
        return toResponse(summary);
    }

    public DischargeSummaryResponse getDischargeSummaryById(Long summaryId) {
        var summary = dischargeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ApiException("DISCHARGE_SUMMARY_NOT_FOUND", "Discharge summary not found"));
        return toResponse(summary);
    }

    public List<DischargeSummaryResponse> getPendingApprovalSummaries() {
        var ctx = tenantContext.current();
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
                .dischargeStatus(summary.getDischargeSummaryStatus().toString())
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
