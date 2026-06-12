package com.katixo.hospital.nursing;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NursingVitalService {

    private final NursingVitalRepository vitalRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    public NursingVitalResponse recordVital(RecordVitalRequest request) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var vital = new NursingVital();
        vital.setTenantId(ctx.getTenantId());
        vital.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        vital.setBranchId(Long.parseLong(ctx.getBranchId()));
        vital.setAdmissionId(request.admissionId);
        vital.setPatientId(request.patientId);
        vital.setRecordedBy(userId);

        vital.setTemperatureCelsius(request.temperatureCelsius);
        vital.setHeartRateBpm(request.heartRateBpm);
        vital.setRespiratoryRate(request.respiratoryRate);
        vital.setSystolicBp(request.systolicBp);
        vital.setDiastolicBp(request.diastolicBp);
        vital.setSpo2Percent(request.spo2Percent);
        vital.setBloodGlucose(request.bloodGlucose);

        vital.setObservations(request.observations);
        vital.setComplaints(request.complaints);
        vital.setPainLevel(request.painLevel);
        vital.setNutritionStatus(request.nutritionStatus);

        // Check for abnormal readings
        boolean abnormal = isAbnormal(vital);
        vital.setIsAbnormal(abnormal);
        if (abnormal) {
            vital.setAbnormalityNotes(generateAbnormalityNotes(vital));
        }

        vital.setRoundStatus(NursingVital.RoundStatus.RECORDED);
        vital.setCreatedBy(userId);
        vital.setUpdatedBy(userId);
        vital.setStatus(BaseEntity.EntityStatus.ACTIVE);

        vital = vitalRepository.save(vital);

        auditService.audit("NursingVital", String.valueOf(vital.getId()),
                AuditLog.AuditAction.CREATE, null,
                Map.of("roundStatus", vital.getRoundStatus().name(),
                        "isAbnormal", abnormal),
                UUID.randomUUID().toString());

        if (abnormal) {
            outboxEventService.publish("NursingVital", String.valueOf(vital.getId()),
                    "vital.abnormal",
                    Map.of("vitalId", vital.getId(),
                            "admissionId", vital.getAdmissionId(),
                            "patientId", vital.getPatientId(),
                            "abnormalityNotes", vital.getAbnormalityNotes()));
        }

        return toResponse(vital);
    }

    public List<NursingVitalResponse> getVitalHistory(Long admissionId) {
        var ctx = TenantContext.get();
        var vitals = vitalRepository.findByTenantIdAndBranchIdAndAdmissionIdOrderByCreatedAtDesc(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), admissionId);
        return vitals.stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NursingVitalResponse> getAbnormalVitals() {
        var ctx = TenantContext.get();
        var vitals = vitalRepository.findByTenantIdAndBranchIdAndIsAbnormalTrue(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        );
        return vitals.stream()
                .map(this::toResponse)
                .toList();
    }

    public NursingVitalResponse reviewVital(Long vitalId) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var vital = vitalRepository.findByIdAndTenantIdAndBranchId(
                        vitalId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("VITAL_NOT_FOUND", "Vital record not found"));

        var beforeStatus = vital.getRoundStatus().name();

        vital.setRoundStatus(NursingVital.RoundStatus.REVIEWED);
        vital.setReviewedBy(userId);
        vital.setReviewedAt(LocalDateTime.now());
        vital.setUpdatedBy(userId);
        vital = vitalRepository.save(vital);

        auditService.audit("NursingVital", String.valueOf(vital.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("roundStatus", beforeStatus),
                Map.of("roundStatus", vital.getRoundStatus().name()),
                UUID.randomUUID().toString());

        return toResponse(vital);
    }

    private boolean isAbnormal(NursingVital vital) {
        if (vital.getTemperatureCelsius() != null) {
            double temp = vital.getTemperatureCelsius().doubleValue();
            if (temp < 36.0 || temp > 38.5) return true;
        }
        if (vital.getHeartRateBpm() != null) {
            int hr = vital.getHeartRateBpm();
            if (hr < 50 || hr > 120) return true;
        }
        if (vital.getRespiratoryRate() != null) {
            int rr = vital.getRespiratoryRate();
            if (rr < 10 || rr > 30) return true;
        }
        if (vital.getSystolicBp() != null && vital.getDiastolicBp() != null) {
            int sys = vital.getSystolicBp();
            int dia = vital.getDiastolicBp();
            if (sys < 90 || sys > 180 || dia < 60 || dia > 120) return true;
        }
        if (vital.getSpo2Percent() != null) {
            double spo2 = vital.getSpo2Percent().doubleValue();
            if (spo2 < 92) return true;
        }
        if (vital.getPainLevel() != null && vital.getPainLevel() > 7) return true;
        return false;
    }

    private String generateAbnormalityNotes(NursingVital vital) {
        StringBuilder notes = new StringBuilder();
        if (vital.getTemperatureCelsius() != null) {
            double temp = vital.getTemperatureCelsius().doubleValue();
            if (temp < 36.0) notes.append("Hypothermia (").append(temp).append("°C). ");
            else if (temp > 38.5) notes.append("High fever (").append(temp).append("°C). ");
        }
        if (vital.getHeartRateBpm() != null) {
            int hr = vital.getHeartRateBpm();
            if (hr < 50) notes.append("Bradycardia (").append(hr).append(" bpm). ");
            else if (hr > 120) notes.append("Tachycardia (").append(hr).append(" bpm). ");
        }
        if (vital.getSpo2Percent() != null && vital.getSpo2Percent().doubleValue() < 92) {
            notes.append("Low oxygen (").append(vital.getSpo2Percent()).append("%). ");
        }
        return notes.toString();
    }

    private NursingVitalResponse toResponse(NursingVital vital) {
        return new NursingVitalResponse(
                vital.getId(),
                vital.getAdmissionId(),
                vital.getPatientId(),
                vital.getRecordedBy(),
                vital.getTemperatureCelsius(),
                vital.getHeartRateBpm(),
                vital.getRespiratoryRate(),
                vital.getSystolicBp(),
                vital.getDiastolicBp(),
                vital.getSpo2Percent(),
                vital.getBloodGlucose(),
                vital.getObservations(),
                vital.getComplaints(),
                vital.getPainLevel(),
                vital.getNutritionStatus(),
                vital.getIsAbnormal(),
                vital.getAbnormalityNotes(),
                vital.getRoundStatus().name(),
                vital.getCreatedAt()
        );
    }

    public static class RecordVitalRequest {
        public Long admissionId;
        public Long patientId;
        public BigDecimal temperatureCelsius;
        public Integer heartRateBpm;
        public Integer respiratoryRate;
        public Integer systolicBp;
        public Integer diastolicBp;
        public BigDecimal spo2Percent;
        public BigDecimal bloodGlucose;
        public String observations;
        public String complaints;
        public Integer painLevel;
        public String nutritionStatus;
    }

    public static class NursingVitalResponse {
        public Long id;
        public Long admissionId;
        public Long patientId;
        public Long recordedBy;
        public BigDecimal temperatureCelsius;
        public Integer heartRateBpm;
        public Integer respiratoryRate;
        public Integer systolicBp;
        public Integer diastolicBp;
        public BigDecimal spo2Percent;
        public BigDecimal bloodGlucose;
        public String observations;
        public String complaints;
        public Integer painLevel;
        public String nutritionStatus;
        public Boolean isAbnormal;
        public String abnormalityNotes;
        public String roundStatus;
        public LocalDateTime recordedAt;

        public NursingVitalResponse(Long id, Long admissionId, Long patientId, Long recordedBy,
                                   BigDecimal temperatureCelsius, Integer heartRateBpm, Integer respiratoryRate,
                                   Integer systolicBp, Integer diastolicBp, BigDecimal spo2Percent,
                                   BigDecimal bloodGlucose, String observations, String complaints,
                                   Integer painLevel, String nutritionStatus, Boolean isAbnormal,
                                   String abnormalityNotes, String roundStatus, LocalDateTime recordedAt) {
            this.id = id;
            this.admissionId = admissionId;
            this.patientId = patientId;
            this.recordedBy = recordedBy;
            this.temperatureCelsius = temperatureCelsius;
            this.heartRateBpm = heartRateBpm;
            this.respiratoryRate = respiratoryRate;
            this.systolicBp = systolicBp;
            this.diastolicBp = diastolicBp;
            this.spo2Percent = spo2Percent;
            this.bloodGlucose = bloodGlucose;
            this.observations = observations;
            this.complaints = complaints;
            this.painLevel = painLevel;
            this.nutritionStatus = nutritionStatus;
            this.isAbnormal = isAbnormal;
            this.abnormalityNotes = abnormalityNotes;
            this.roundStatus = roundStatus;
            this.recordedAt = recordedAt;
        }
    }
}
