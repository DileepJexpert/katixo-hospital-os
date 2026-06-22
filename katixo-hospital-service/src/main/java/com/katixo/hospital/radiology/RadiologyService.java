package com.katixo.hospital.radiology;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.notification.NotificationService;
import com.katixo.hospital.notification.NotificationType;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Radiology orders + reporting (mirrors the lab flow). Order a study, mark it
 * performed, then file the report (findings + impression) which releases it.
 * No journals — radiology charges bill via the tariff/charge path.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RadiologyService {

    private final RadiologyOrderRepository orderRepository;
    private final AuditService auditService;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;

    public RadiologyOrder order(Long patientId, Long referringDoctorId, RadiologyOrder.Modality modality,
                                String studyName, String notes) {
        if (patientId == null || referringDoctorId == null) {
            throw new BusinessException("RAD_INVALID", "Patient and referring doctor are required");
        }
        if (modality == null) {
            throw new BusinessException("RAD_MODALITY_REQUIRED", "Modality is required");
        }
        if (studyName == null || studyName.isBlank()) {
            throw new BusinessException("RAD_STUDY_REQUIRED", "Study name is required");
        }
        RadiologyOrder o = new RadiologyOrder();
        o.setOrderNumber("RAD-" + orderRepository.nextOrderSequence());
        o.setPatientId(patientId);
        o.setReferringDoctorId(referringDoctorId);
        o.setModality(modality);
        o.setStudyName(studyName.trim());
        o.setOrderDate(LocalDate.now());
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.ORDERED);
        o.setNotes(notes);
        stamp(o);
        RadiologyOrder saved = orderRepository.save(o);
        auditService.audit("RadiologyOrder", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("orderNumber", saved.getOrderNumber(), "modality", modality.name(),
                        "study", saved.getStudyName()), UUID.randomUUID().toString());
        log.info("Radiology order {} created: {} {}", saved.getOrderNumber(), modality, saved.getStudyName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RadiologyOrder> list(RadiologyOrder.RadiologyStatus status, int limit) {
        var ctx = TenantContext.get();
        int capped = Math.min(Math.max(limit, 1), 200);
        var page = PageRequest.of(0, capped);
        return status == null
                ? orderRepository.findByTenantIdAndBranchIdOrderByIdDesc(ctx.getTenantId(), branchId(), page)
                : orderRepository.findByTenantIdAndBranchIdAndRadiologyStatusOrderByIdDesc(
                        ctx.getTenantId(), branchId(), status, page);
    }

    @Transactional(readOnly = true)
    public RadiologyOrder get(Long id) {
        return getOwned(id);
    }

    public RadiologyOrder markPerformed(Long id) {
        RadiologyOrder o = getOwned(id);
        if (o.getRadiologyStatus() != RadiologyOrder.RadiologyStatus.ORDERED) {
            throw new BusinessException("INVALID_STATE", "Only an ordered study can be marked performed");
        }
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.PERFORMED);
        o.setUpdatedBy(userId());
        return audited(orderRepository.save(o), "PERFORMED");
    }

    public RadiologyOrder report(Long id, String findings, String impression) {
        RadiologyOrder o = getOwned(id);
        if (o.getRadiologyStatus() != RadiologyOrder.RadiologyStatus.PERFORMED
                && o.getRadiologyStatus() != RadiologyOrder.RadiologyStatus.ORDERED) {
            throw new BusinessException("INVALID_STATE", "Only an ordered/performed study can be reported");
        }
        if (impression == null || impression.isBlank()) {
            throw new BusinessException("RAD_IMPRESSION_REQUIRED", "An impression is required to report");
        }
        o.setFindings(findings);
        o.setImpression(impression);
        o.setRadiologistId(userId());
        o.setReportedAt(LocalDateTime.now());
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.REPORTED);
        o.setUpdatedBy(userId());
        RadiologyOrder saved = audited(orderRepository.save(o), "REPORTED");

        // Best-effort "your report is ready" to the patient (consent-gated, never blocks). Mirrors lab.
        var ctx = TenantContext.get();
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(
                saved.getPatientId(), ctx.getTenantId(), branchId()).orElse(null);
        String reportLabel = saved.getStudyName() != null ? saved.getStudyName()
                : (saved.getModality() != null ? saved.getModality() + " report" : "Radiology report");
        notificationService.notifyPatient(NotificationType.REPORT_READY, patient, Map.of(
                "name", patient == null || patient.getFullName() == null ? "" : patient.getFullName(),
                "report", reportLabel), "RadiologyReport", saved.getId());
        return saved;
    }

    public RadiologyOrder cancel(Long id, String reason) {
        RadiologyOrder o = getOwned(id);
        if (o.getRadiologyStatus() == RadiologyOrder.RadiologyStatus.REPORTED
                || o.getRadiologyStatus() == RadiologyOrder.RadiologyStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "Cannot cancel a " + o.getRadiologyStatus() + " study");
        }
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            o.setNotes((o.getNotes() == null ? "" : o.getNotes() + " | ") + "Cancelled: " + reason);
        }
        o.setUpdatedBy(userId());
        return audited(orderRepository.save(o), "CANCELLED");
    }

    private RadiologyOrder audited(RadiologyOrder o, String status) {
        auditService.audit("RadiologyOrder", String.valueOf(o.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("status", status), UUID.randomUUID().toString());
        return o;
    }

    private RadiologyOrder getOwned(Long id) {
        var ctx = TenantContext.get();
        return orderRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("RAD_NOT_FOUND", "Radiology order not found: " + id));
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
