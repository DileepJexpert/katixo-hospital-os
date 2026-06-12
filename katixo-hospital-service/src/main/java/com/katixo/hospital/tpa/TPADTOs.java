package com.katixo.hospital.tpa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RegisterTPACaseRequest {
    public Long admissionId;
    public Long patientId;
    public String insurerName;
    public String policyNumber;
    public String memberId;
    public String policyHolderName;
    public BigDecimal sumInsured;
    public BigDecimal approvedAmount;
    public String tpaCoordinator;
    public String tpaPhone;
    public String notes;
    public List<String> requiredDocuments;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SubmitPreauthRequest {
    public String preauthRefNumber;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ApprovePreauthRequest {
    public BigDecimal approvedAmount;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RejectPreauthRequest {
    public String reason;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SubmitClaimRequest {
    public String claimNumber;
    public BigDecimal claimAmount;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ApproveClaimRequest {
    public BigDecimal approvedAmount;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RejectClaimRequest {
    public String reason;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SubmitDocumentRequest {
    public String fileUrl;
    public String notes;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TPACaseResponse {
    public Long id;
    public String caseNumber;
    public Long admissionId;
    public Long patientId;
    public String insurerName;
    public String policyNumber;
    public String memberId;
    public String policyHolderName;
    public BigDecimal sumInsured;
    public BigDecimal approvedAmount;
    public String caseStatus;
    public String preauthRefNumber;
    public LocalDateTime preauthDate;
    public LocalDateTime preauthApprovedAt;
    public String claimNumber;
    public LocalDateTime claimSubmittedAt;
    public BigDecimal claimAmount;
    public LocalDateTime claimApprovedAt;
    public String tpaCoordinator;
    public String tpaPhone;
    public Long coordinatorId;
    public String notes;
    public List<TPADocumentResponse> documents;
    public LocalDateTime createdAt;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TPADocumentResponse {
    public Long id;
    public Long tpaCaseId;
    public String documentType;
    public Boolean required;
    public Boolean submitted;
    public LocalDateTime submittedAt;
    public Long submittedBy;
    public String fileUrl;
    public String notes;
}
