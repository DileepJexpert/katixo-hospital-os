package com.katixo.hospital.nabh;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NABH quality management: quality indicators + periodic readings, and incident
 * (adverse-event) reporting with a REPORTED → UNDER_REVIEW → CLOSED lifecycle.
 * No accounting — this is clinical-governance data.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NabhService {

    private final QualityIndicatorRepository indicatorRepository;
    private final QualityIndicatorReadingRepository readingRepository;
    private final IncidentReportRepository incidentRepository;
    private final AuditService auditService;

    // ---------------- quality indicators ----------------

    public QualityIndicator createIndicator(String code, String name, String category,
                                            String unit, BigDecimal targetValue) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("QI_CODE_REQUIRED", "Indicator code is required");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("QI_NAME_REQUIRED", "Indicator name is required");
        }
        var ctx = TenantContext.get();
        if (indicatorRepository.existsByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code.trim())) {
            throw new BusinessException("QI_CODE_EXISTS", "An indicator with code " + code + " already exists");
        }
        QualityIndicator qi = new QualityIndicator();
        qi.setCode(code.trim());
        qi.setName(name.trim());
        qi.setCategory(category);
        qi.setUnit(unit);
        qi.setTargetValue(targetValue);
        qi.setActive(true);
        stamp(qi);
        QualityIndicator saved = indicatorRepository.save(qi);
        auditService.audit("QualityIndicator", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("code", saved.getCode(), "name", saved.getName()), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<QualityIndicator> listIndicators() {
        var ctx = TenantContext.get();
        return indicatorRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId());
    }

    public QualityIndicatorReading recordReading(Long indicatorId, String period, BigDecimal value,
                                                 BigDecimal numerator, BigDecimal denominator, String notes) {
        QualityIndicator qi = getIndicator(indicatorId);
        if (period == null || period.isBlank()) {
            throw new BusinessException("QI_PERIOD_REQUIRED", "Reading period is required");
        }
        if (value == null) {
            throw new BusinessException("QI_VALUE_REQUIRED", "Reading value is required");
        }
        QualityIndicatorReading r = new QualityIndicatorReading();
        r.setIndicatorId(qi.getId());
        r.setPeriod(period.trim());
        r.setValue(value);
        r.setNumerator(numerator);
        r.setDenominator(denominator);
        r.setNotes(notes);
        stamp(r);
        QualityIndicatorReading saved = readingRepository.save(r);
        auditService.audit("QualityIndicatorReading", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("indicator", qi.getCode(), "period", saved.getPeriod(), "value", value),
                UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<QualityIndicatorReading> listReadings(Long indicatorId) {
        getIndicator(indicatorId); // tenant ownership check
        return readingRepository.findByTenantIdAndIndicatorIdOrderByPeriodDesc(
                TenantContext.get().getTenantId(), indicatorId);
    }

    // ---------------- incident reports ----------------

    public IncidentReport reportIncident(LocalDate incidentDate, IncidentReport.IncidentType type,
                                         IncidentReport.Severity severity, String location, Long patientId,
                                         String description, String immediateAction) {
        if (type == null || severity == null) {
            throw new BusinessException("INCIDENT_INVALID", "Incident type and severity are required");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessException("INCIDENT_DESC_REQUIRED", "Description is required");
        }
        IncidentReport i = new IncidentReport();
        i.setReportNumber("INC-" + incidentRepository.nextIncidentSequence());
        i.setIncidentDate(incidentDate == null ? LocalDate.now() : incidentDate);
        i.setIncidentType(type);
        i.setSeverity(severity);
        i.setLocation(location);
        i.setPatientId(patientId);
        i.setDescription(description.trim());
        i.setImmediateAction(immediateAction);
        i.setIncidentStatus(IncidentReport.IncidentStatus.REPORTED);
        stamp(i);
        IncidentReport saved = incidentRepository.save(i);
        auditService.audit("IncidentReport", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("reportNumber", saved.getReportNumber(), "type", type.name(),
                        "severity", severity.name()), UUID.randomUUID().toString());
        log.info("Incident {} reported: {} / {}", saved.getReportNumber(), type, severity);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<IncidentReport> listIncidents(IncidentReport.IncidentStatus status, int limit) {
        var ctx = TenantContext.get();
        int capped = Math.min(Math.max(limit, 1), 200);
        var page = PageRequest.of(0, capped);
        return status == null
                ? incidentRepository.findByTenantIdAndBranchIdOrderByIdDesc(ctx.getTenantId(), branchId(), page)
                : incidentRepository.findByTenantIdAndBranchIdAndIncidentStatusOrderByIdDesc(
                        ctx.getTenantId(), branchId(), status, page);
    }

    @Transactional(readOnly = true)
    public IncidentReport getIncident(Long id) {
        return getIncidentOwned(id);
    }

    /** Move a reported incident into review. */
    public IncidentReport startReview(Long id) {
        IncidentReport i = getIncidentOwned(id);
        if (i.getIncidentStatus() != IncidentReport.IncidentStatus.REPORTED) {
            throw new BusinessException("INVALID_STATE", "Only a reported incident can be moved to review");
        }
        i.setIncidentStatus(IncidentReport.IncidentStatus.UNDER_REVIEW);
        i.setUpdatedBy(userId());
        return auditedIncident(incidentRepository.save(i), "UNDER_REVIEW");
    }

    /** Close an incident with root cause + corrective action (NABH requires both). */
    public IncidentReport closeIncident(Long id, String rootCause, String correctiveAction) {
        IncidentReport i = getIncidentOwned(id);
        if (i.getIncidentStatus() == IncidentReport.IncidentStatus.CLOSED) {
            throw new BusinessException("INVALID_STATE", "Incident is already closed");
        }
        if (rootCause == null || rootCause.isBlank() || correctiveAction == null || correctiveAction.isBlank()) {
            throw new BusinessException("INCIDENT_CLOSURE_INCOMPLETE",
                    "Root cause and corrective action are required to close an incident");
        }
        i.setRootCause(rootCause);
        i.setCorrectiveAction(correctiveAction);
        i.setIncidentStatus(IncidentReport.IncidentStatus.CLOSED);
        i.setClosedBy(userId());
        i.setClosedAt(LocalDateTime.now());
        i.setUpdatedBy(userId());
        return auditedIncident(incidentRepository.save(i), "CLOSED");
    }

    // ---------------- helpers ----------------

    private IncidentReport auditedIncident(IncidentReport i, String status) {
        auditService.audit("IncidentReport", String.valueOf(i.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("status", status), UUID.randomUUID().toString());
        return i;
    }

    private QualityIndicator getIndicator(Long id) {
        var ctx = TenantContext.get();
        return indicatorRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("QI_NOT_FOUND", "Quality indicator not found: " + id));
    }

    private IncidentReport getIncidentOwned(Long id) {
        var ctx = TenantContext.get();
        return incidentRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("INCIDENT_NOT_FOUND", "Incident not found: " + id));
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
