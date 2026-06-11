package com.katixo.hospital.tpa;

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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TPAService {

    private final TPACaseRepository caseRepository;
    private final TPADocumentRepository documentRepository;
    private final AuditService auditService;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    private static final String CASE_NUMBER_FORMAT = "CASE-%d-%05d";

    public TPACaseResponse registerCase(RegisterTPACaseRequest request) {
        var ctx = tenantContext.current();
        var caseNumber = generateCaseNumber();

        var tpaCase = new TPACase();
        tpaCase.setTenantId(ctx.getTenantId());
        tpaCase.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        tpaCase.setBranchId(Long.parseLong(ctx.getBranchId()));
        tpaCase.setCaseNumber(caseNumber);
        tpaCase.setAdmissionId(request.admissionId);
        tpaCase.setPatientId(request.patientId);
        tpaCase.setInsurerName(request.insurerName);
        tpaCase.setPolicyNumber(request.policyNumber);
        tpaCase.setMemberId(request.memberId);
        tpaCase.setPolicyHolderName(request.policyHolderName);
        tpaCase.setSumInsured(request.sumInsured);
        tpaCase.setApprovedAmount(request.approvedAmount);
        tpaCase.setCaseStatus(TPACase.CaseStatus.REGISTERED);
        tpaCase.setTpaCoordinator(request.tpaCoordinator);
        tpaCase.setTpaPhone(request.tpaPhone);
        tpaCase.setNotes(request.notes);
        tpaCase.setCreatedBy(ctx.getCurrentUserId());
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        List<TPADocument> documents = request.requiredDocuments.stream()
                .map(docType -> {
                    var doc = new TPADocument();
                    doc.setTenantId(ctx.getTenantId());
                    doc.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
                    doc.setBranchId(Long.parseLong(ctx.getBranchId()));
                    doc.setTpaCaseId(tpaCase.getId());
                    doc.setDocumentType(docType);
                    doc.setRequired(true);
                    doc.setSubmitted(false);
                    doc.setCreatedBy(ctx.getCurrentUserId());
                    doc.setUpdatedBy(ctx.getCurrentUserId());
                    return doc;
                })
                .collect(Collectors.toList());

        if (!documents.isEmpty()) {
            documentRepository.saveAll(documents);
        }

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("REGISTER_TPA_CASE")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.case.registered",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public List<TPACaseResponse> getCasesByStatus(TPACase.CaseStatus status) {
        var ctx = tenantContext.current();
        var cases = caseRepository.findByTenantIdAndBranchIdAndCaseStatus(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                status
        );
        return cases.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<TPACaseResponse> getCasesByAdmission(Long admissionId) {
        var cases = caseRepository.findByAdmissionId(admissionId);
        return cases.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TPACaseResponse getCaseById(Long caseId) {
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));
        return toResponse(tpaCase);
    }

    public TPACaseResponse submitPreauth(Long caseId, SubmitPreauthRequest request) {
        var ctx = tenantContext.current();
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.REGISTERED) {
            throw new ApiException("INVALID_STATUS", "Case must be in REGISTERED status");
        }

        tpaCase.setCaseStatus(TPACase.CaseStatus.PREAUTH_PENDING);
        tpaCase.setPreauthRefNumber(request.preauthRefNumber);
        tpaCase.setPreauthDate(LocalDateTime.now());
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("SUBMIT_PREAUTH")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.preauth.submitted",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public TPACaseResponse approvePreauth(Long caseId, ApprovePreauthRequest request) {
        var ctx = tenantContext.current();
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.PREAUTH_PENDING) {
            throw new ApiException("INVALID_STATUS", "Case must be in PREAUTH_PENDING status");
        }

        tpaCase.setCaseStatus(TPACase.CaseStatus.PREAUTH_APPROVED);
        tpaCase.setPreauthApprovedAt(LocalDateTime.now());
        tpaCase.setApprovedAmount(request.approvedAmount);
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("APPROVE_PREAUTH")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.preauth.approved",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public TPACaseResponse rejectPreauth(Long caseId, RejectPreauthRequest request) {
        var ctx = tenantContext.current();
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.PREAUTH_PENDING) {
            throw new ApiException("INVALID_STATUS", "Case must be in PREAUTH_PENDING status");
        }

        tpaCase.setCaseStatus(TPACase.CaseStatus.PREAUTH_REJECTED);
        tpaCase.setNotes(request.reason);
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("REJECT_PREAUTH")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.preauth.rejected",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public TPACaseResponse submitClaim(Long caseId, SubmitClaimRequest request) {
        var ctx = tenantContext.current();
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.PREAUTH_APPROVED) {
            throw new ApiException("INVALID_STATUS", "Case must be in PREAUTH_APPROVED status");
        }

        tpaCase.setCaseStatus(TPACase.CaseStatus.CLAIM_SUBMITTED);
        tpaCase.setClaimNumber(request.claimNumber);
        tpaCase.setClaimSubmittedAt(LocalDateTime.now());
        tpaCase.setClaimAmount(request.claimAmount);
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("SUBMIT_CLAIM")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.claim.submitted",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public TPACaseResponse approveClaim(Long caseId, ApproveClaimRequest request) {
        var ctx = tenantContext.current();
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.CLAIM_SUBMITTED) {
            throw new ApiException("INVALID_STATUS", "Case must be in CLAIM_SUBMITTED status");
        }

        tpaCase.setCaseStatus(TPACase.CaseStatus.CLAIM_APPROVED);
        tpaCase.setClaimApprovedAt(LocalDateTime.now());
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("APPROVE_CLAIM")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.claim.approved",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public TPACaseResponse rejectClaim(Long caseId, RejectClaimRequest request) {
        var ctx = tenantContext.current();
        var tpaCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ApiException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.CLAIM_SUBMITTED) {
            throw new ApiException("INVALID_STATUS", "Case must be in CLAIM_SUBMITTED status");
        }

        tpaCase.setCaseStatus(TPACase.CaseStatus.CLAIM_REJECTED);
        tpaCase.setNotes(request.reason);
        tpaCase.setUpdatedBy(ctx.getCurrentUserId());

        tpaCase = caseRepository.save(tpaCase);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("REJECT_CLAIM")
                .entityType("TPACase")
                .entityId(tpaCase.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.claim.rejected",
                "TPACase",
                tpaCase.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(tpaCase);
    }

    public List<TPADocumentResponse> getRequiredDocuments(Long caseId) {
        var documents = documentRepository.findByTpaCaseIdAndRequiredTrue(caseId);
        return documents.stream().map(this::toDocumentResponse).collect(Collectors.toList());
    }

    public List<TPADocumentResponse> getPendingDocuments(Long caseId) {
        var documents = documentRepository.findByTpaCaseIdAndSubmittedFalse(caseId);
        return documents.stream().map(this::toDocumentResponse).collect(Collectors.toList());
    }

    public TPADocumentResponse submitDocument(Long documentId, SubmitDocumentRequest request) {
        var ctx = tenantContext.current();
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException("DOCUMENT_NOT_FOUND", "Document not found"));

        document.setSubmitted(true);
        document.setSubmittedAt(LocalDateTime.now());
        document.setSubmittedBy(ctx.getCurrentUserId());
        document.setFileUrl(request.fileUrl);
        document.setNotes(request.notes);
        document.setUpdatedBy(ctx.getCurrentUserId());

        document = documentRepository.save(document);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("SUBMIT_TPA_DOCUMENT")
                .entityType("TPADocument")
                .entityId(document.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "tpa.document.submitted",
                "TPADocument",
                document.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toDocumentResponse(document);
    }

    public TPADocumentResponse uploadDocument(Long documentId, MultipartFile file, String notes) {
        var ctx = tenantContext.current();
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException("DOCUMENT_NOT_FOUND", "Document not found"));

        if (file.isEmpty()) {
            throw new ApiException("FILE_EMPTY", "File cannot be empty");
        }

        try {
            String fileName = generateFileUrl(documentId, file.getOriginalFilename());

            document.setSubmitted(true);
            document.setSubmittedAt(LocalDateTime.now());
            document.setSubmittedBy(ctx.getCurrentUserId());
            document.setFileUrl(fileName);
            if (notes != null && !notes.isBlank()) {
                document.setNotes(notes);
            }
            document.setUpdatedBy(ctx.getCurrentUserId());

            document = documentRepository.save(document);

            auditService.log(AuditLog.builder()
                    .actorId(ctx.getCurrentUserId())
                    .action("UPLOAD_TPA_DOCUMENT")
                    .entityType("TPADocument")
                    .entityId(document.getId())
                    .tenantId(ctx.getTenantId())
                    .branchId(Long.parseLong(ctx.getBranchId()))
                    .build());

            outboxPublisher.publish(new OutboxEvent(
                    "tpa.document.uploaded",
                    "TPADocument",
                    document.getId(),
                    ctx.getTenantId(),
                    Long.parseLong(ctx.getBranchId())
            ));

            return toDocumentResponse(document);
        } catch (Exception e) {
            log.error("Failed to upload document: {}", e.getMessage(), e);
            throw new ApiException("UPLOAD_FAILED", "Failed to upload document: " + e.getMessage());
        }
    }

    private TPACaseResponse toResponse(TPACase tpaCase) {
        var documents = documentRepository.findByTpaCaseId(tpaCase.getId());
        var documentResponses = documents.stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());

        return TPACaseResponse.builder()
                .id(tpaCase.getId())
                .caseNumber(tpaCase.getCaseNumber())
                .admissionId(tpaCase.getAdmissionId())
                .patientId(tpaCase.getPatientId())
                .insurerName(tpaCase.getInsurerName())
                .policyNumber(tpaCase.getPolicyNumber())
                .memberId(tpaCase.getMemberId())
                .policyHolderName(tpaCase.getPolicyHolderName())
                .sumInsured(tpaCase.getSumInsured())
                .approvedAmount(tpaCase.getApprovedAmount())
                .caseStatus(tpaCase.getCaseStatus().toString())
                .preauthRefNumber(tpaCase.getPreauthRefNumber())
                .preauthDate(tpaCase.getPreauthDate())
                .preauthApprovedAt(tpaCase.getPreauthApprovedAt())
                .claimNumber(tpaCase.getClaimNumber())
                .claimSubmittedAt(tpaCase.getClaimSubmittedAt())
                .claimAmount(tpaCase.getClaimAmount())
                .claimApprovedAt(tpaCase.getClaimApprovedAt())
                .tpaCoordinator(tpaCase.getTpaCoordinator())
                .tpaPhone(tpaCase.getTpaPhone())
                .coordinatorId(tpaCase.getCoordinatorId())
                .notes(tpaCase.getNotes())
                .documents(documentResponses)
                .createdAt(tpaCase.getCreatedAt())
                .build();
    }

    private TPADocumentResponse toDocumentResponse(TPADocument document) {
        return TPADocumentResponse.builder()
                .id(document.getId())
                .tpaCaseId(document.getTpaCaseId())
                .documentType(document.getDocumentType())
                .required(document.getRequired())
                .submitted(document.getSubmitted())
                .submittedAt(document.getSubmittedAt())
                .submittedBy(document.getSubmittedBy())
                .fileUrl(document.getFileUrl())
                .notes(document.getNotes())
                .build();
    }

    private String generateCaseNumber() {
        var ctx = tenantContext.current();
        var month = YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        var sequence = 1;
        var prefix = String.format(CASE_NUMBER_FORMAT, Long.parseLong(month), sequence);
        return prefix;
    }

    private String generateFileUrl(Long documentId, String originalFilename) {
        var uuid = UUID.randomUUID().toString();
        var extension = getFileExtension(originalFilename);
        return String.format("tpa/documents/%d/%s%s", documentId, uuid, extension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
