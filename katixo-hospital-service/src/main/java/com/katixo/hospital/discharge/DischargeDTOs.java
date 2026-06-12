package com.katixo.hospital.discharge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CreateDischargeSummaryRequest {
    public Long admissionId;
    public Long patientId;
    public String chiefComplaints;
    public String diagnosis;
    public String treatmentSummary;
    public String procedures;
    public String medications;
    public String followUpInstructions;
    public String restrictions;
    public String warningSymptoms;
    public String dischargeType;
    public String additionalNotes;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UpdateDischargeSummaryRequest {
    public String diagnosis;
    public String treatmentSummary;
    public String medications;
    public String followUpInstructions;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DischargeSummaryResponse {
    public Long id;
    public Long admissionId;
    public Long patientId;
    public String chiefComplaints;
    public String diagnosis;
    public String treatmentSummary;
    public String procedures;
    public String medications;
    public String followUpInstructions;
    public String restrictions;
    public String warningSymptoms;
    public String dischargeType;
    public String dischargeStatus;
    public Long preparedBy;
    public LocalDateTime preparedAt;
    public Long approvedBy;
    public LocalDateTime approvedAt;
    public Long finishedBy;
    public LocalDateTime finishedAt;
    public String fileUrl;
    public String additionalNotes;
    public LocalDateTime createdAt;
}
