package com.katixo.hospital.tpa;

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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
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
    private final OutboxEventService outboxEventService;

    private static final String CASE_NUMBER_FORMAT = "CASE-%d-%05d";

    public TPACaseResponse registerCase(RegisterTPACaseRequest request) {
        var ctx = TenantContext.get();
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
        tpaCase.setCreatedBy(Long.parseLong(ctx.getUserId()));
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        tpaCase.setStatus(BaseEntity.EntityStatus.ACTIVE);

        var savedCase = caseRepository.save(tpaCase);

        List<TPADocument> documents = request.requiredDocuments == null ? List.of()
                : request.requiredDocuments.stream()
                .map(docType -> {
                    var doc = new TPADocument();
                    doc.setTenantId(ctx.getTenantId());
                    doc.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
                    doc.setBranchId(Long.parseLong(ctx.getBranchId()));
                    doc.setTpaCaseId(savedCase.getId());
                    doc.setDocumentType(docType);
                    doc.setRequired(true);
                    doc.setSubmitted(false);
                    doc.setCreatedBy(Long.parseLong(ctx.getUserId()));
                    doc.setUpdatedBy(Long.parseLong(ctx.getUserId()));
                    doc.setStatus(BaseEntity.EntityStatus.ACTIVE);
                    return doc;
                })
                .collect(Collectors.toList());

        if (!documents.isEmpty()) {
            documentRepository.saveAll(documents);
        }

        auditService.audit("TPACase", String.valueOf(savedCase.getId()),
                AuditLog.AuditAction.CREATE,
                null,
                Map.of("caseNumber", savedCase.getCaseNumber(),
                        "caseStatus", savedCase.getCaseStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(savedCase.getId()),
                "tpa.case.registered",
                Map.of("caseId", savedCase.getId(),
                        "caseNumber", savedCase.getCaseNumber(),
                        "admissionId", savedCase.getAdmissionId()));

        return toResponse(savedCase);
    }

    public List<TPACaseResponse> getCasesByStatus(TPACase.CaseStatus status) {
        var ctx = TenantContext.get();
        var cases = caseRepository.findByTenantIdAndBranchIdAndCaseStatus(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                status
        );
        return cases.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<TPACaseResponse> getCasesByAdmission(Long admissionId) {
        var ctx = TenantContext.get();
        var cases = caseRepository.findByTenantIdAndBranchIdAndAdmissionId(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), admissionId);
        return cases.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TPACaseResponse getCaseById(Long caseId) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));
        return toResponse(tpaCase);
    }

    public TPACaseResponse submitPreauth(Long caseId, SubmitPreauthRequest request) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.REGISTERED) {
            throw new BusinessException("INVALID_STATUS", "Case must be in REGISTERED status");
        }

        var oldStatus = tpaCase.getCaseStatus();
        tpaCase.setCaseStatus(TPACase.CaseStatus.PREAUTH_PENDING);
        tpaCase.setPreauthRefNumber(request.preauthRefNumber);
        tpaCase.setPreauthDate(LocalDateTime.now());
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        tpaCase = caseRepository.save(tpaCase);

        auditService.audit("TPACase", String.valueOf(tpaCase.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("caseStatus", oldStatus.name()),
                Map.of("caseStatus", tpaCase.getCaseStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(tpaCase.getId()),
                "tpa.preauth.submitted",
                Map.of("caseId", tpaCase.getId(),
                        "preauthRefNumber", String.valueOf(tpaCase.getPreauthRefNumber())));

        return toResponse(tpaCase);
    }

    public TPACaseResponse approvePreauth(Long caseId, ApprovePreauthRequest request) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.PREAUTH_PENDING) {
            throw new BusinessException("INVALID_STATUS", "Case must be in PREAUTH_PENDING status");
        }

        var oldStatus = tpaCase.getCaseStatus();
        tpaCase.setCaseStatus(TPACase.CaseStatus.PREAUTH_APPROVED);
        tpaCase.setPreauthApprovedAt(LocalDateTime.now());
        tpaCase.setApprovedAmount(request.approvedAmount);
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        tpaCase = caseRepository.save(tpaCase);

        auditService.audit("TPACase", String.valueOf(tpaCase.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("caseStatus", oldStatus.name()),
                Map.of("caseStatus", tpaCase.getCaseStatus().name(),
                        "approvedAmount", String.valueOf(tpaCase.getApprovedAmount())),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(tpaCase.getId()),
                "tpa.preauth.approved",
                Map.of("caseId", tpaCase.getId(),
                        "approvedAmount", String.valueOf(tpaCase.getApprovedAmount())));

        return toResponse(tpaCase);
    }

    public TPACaseResponse rejectPreauth(Long caseId, RejectPreauthRequest request) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.PREAUTH_PENDING) {
            throw new BusinessException("INVALID_STATUS", "Case must be in PREAUTH_PENDING status");
        }

        var oldStatus = tpaCase.getCaseStatus();
        tpaCase.setCaseStatus(TPACase.CaseStatus.PREAUTH_REJECTED);
        tpaCase.setNotes(request.reason);
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        tpaCase = caseRepository.save(tpaCase);

        auditService.audit("TPACase", String.valueOf(tpaCase.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("caseStatus", oldStatus.name()),
                Map.of("caseStatus", tpaCase.getCaseStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(tpaCase.getId()),
                "tpa.preauth.rejected",
                Map.of("caseId", tpaCase.getId()));

        return toResponse(tpaCase);
    }

    public TPACaseResponse submitClaim(Long caseId, SubmitClaimRequest request) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.PREAUTH_APPROVED) {
            throw new BusinessException("INVALID_STATUS", "Case must be in PREAUTH_APPROVED status");
        }

        var oldStatus = tpaCase.getCaseStatus();
        tpaCase.setCaseStatus(TPACase.CaseStatus.CLAIM_SUBMITTED);
        tpaCase.setClaimNumber(request.claimNumber);
        tpaCase.setClaimSubmittedAt(LocalDateTime.now());
        tpaCase.setClaimAmount(request.claimAmount);
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        tpaCase = caseRepository.save(tpaCase);

        auditService.audit("TPACase", String.valueOf(tpaCase.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("caseStatus", oldStatus.name()),
                Map.of("caseStatus", tpaCase.getCaseStatus().name(),
                        "claimNumber", String.valueOf(tpaCase.getClaimNumber())),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(tpaCase.getId()),
                "tpa.claim.submitted",
                Map.of("caseId", tpaCase.getId(),
                        "claimNumber", String.valueOf(tpaCase.getClaimNumber()),
                        "claimAmount", String.valueOf(tpaCase.getClaimAmount())));

        return toResponse(tpaCase);
    }

    public TPACaseResponse approveClaim(Long caseId, ApproveClaimRequest request) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.CLAIM_SUBMITTED) {
            throw new BusinessException("INVALID_STATUS", "Case must be in CLAIM_SUBMITTED status");
        }

        var oldStatus = tpaCase.getCaseStatus();
        tpaCase.setCaseStatus(TPACase.CaseStatus.CLAIM_APPROVED);
        tpaCase.setClaimApprovedAt(LocalDateTime.now());
        if (request.approvedAmount != null) {
            tpaCase.setApprovedAmount(request.approvedAmount);
        }
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        tpaCase = caseRepository.save(tpaCase);

        auditService.audit("TPACase", String.valueOf(tpaCase.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("caseStatus", oldStatus.name()),
                Map.of("caseStatus", tpaCase.getCaseStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(tpaCase.getId()),
                "tpa.claim.approved",
                Map.of("caseId", tpaCase.getId()));

        return toResponse(tpaCase);
    }

    public TPACaseResponse rejectClaim(Long caseId, RejectClaimRequest request) {
        var ctx = TenantContext.get();
        var tpaCase = caseRepository.findByIdAndTenantIdAndBranchId(caseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("TPA_CASE_NOT_FOUND", "TPA case not found"));

        if (tpaCase.getCaseStatus() != TPACase.CaseStatus.CLAIM_SUBMITTED) {
            throw new BusinessException("INVALID_STATUS", "Case must be in CLAIM_SUBMITTED status");
        }

        var oldStatus = tpaCase.getCaseStatus();
        tpaCase.setCaseStatus(TPACase.CaseStatus.CLAIM_REJECTED);
        tpaCase.setNotes(request.reason);
        tpaCase.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        tpaCase = caseRepository.save(tpaCase);

        auditService.audit("TPACase", String.valueOf(tpaCase.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("caseStatus", oldStatus.name()),
                Map.of("caseStatus", tpaCase.getCaseStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPACase", String.valueOf(tpaCase.getId()),
                "tpa.claim.rejected",
                Map.of("caseId", tpaCase.getId()));

        return toResponse(tpaCase);
    }

    public List<TPADocumentResponse> getRequiredDocuments(Long caseId) {
        var ctx = TenantContext.get();
        var documents = documentRepository.findByTenantIdAndTpaCaseIdAndRequiredTrue(ctx.getTenantId(), caseId);
        return documents.stream().map(this::toDocumentResponse).collect(Collectors.toList());
    }

    public List<TPADocumentResponse> getPendingDocuments(Long caseId) {
        var ctx = TenantContext.get();
        var documents = documentRepository.findByTenantIdAndTpaCaseIdAndSubmittedFalse(ctx.getTenantId(), caseId);
        return documents.stream().map(this::toDocumentResponse).collect(Collectors.toList());
    }

    public TPADocumentResponse submitDocument(Long documentId, SubmitDocumentRequest request) {
        var ctx = TenantContext.get();
        var document = documentRepository.findByIdAndTenantIdAndBranchId(documentId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DOCUMENT_NOT_FOUND", "Document not found"));

        var wasSubmitted = Boolean.TRUE.equals(document.getSubmitted());
        document.setSubmitted(true);
        document.setSubmittedAt(LocalDateTime.now());
        document.setSubmittedBy(Long.parseLong(ctx.getUserId()));
        document.setFileUrl(request.fileUrl);
        document.setNotes(request.notes);
        document.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        document = documentRepository.save(document);

        auditService.audit("TPADocument", String.valueOf(document.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("submitted", String.valueOf(wasSubmitted)),
                Map.of("submitted", "true",
                        "documentType", document.getDocumentType()),
                UUID.randomUUID().toString());

        outboxEventService.publish("TPADocument", String.valueOf(document.getId()),
                "tpa.document.submitted",
                Map.of("documentId", document.getId(),
                        "tpaCaseId", document.getTpaCaseId()));

        return toDocumentResponse(document);
    }

    public TPADocumentResponse uploadDocument(Long documentId, MultipartFile file, String notes) {
        var ctx = TenantContext.get();
        var document = documentRepository.findByIdAndTenantIdAndBranchId(documentId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("DOCUMENT_NOT_FOUND", "Document not found"));

        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "File cannot be empty");
        }

        try {
            String fileName = generateFileUrl(documentId, file.getOriginalFilename());

            var wasSubmitted = Boolean.TRUE.equals(document.getSubmitted());
            document.setSubmitted(true);
            document.setSubmittedAt(LocalDateTime.now());
            document.setSubmittedBy(Long.parseLong(ctx.getUserId()));
            document.setFileUrl(fileName);
            if (notes != null && !notes.isBlank()) {
                document.setNotes(notes);
            }
            document.setUpdatedBy(Long.parseLong(ctx.getUserId()));

            document = documentRepository.save(document);

            auditService.audit("TPADocument", String.valueOf(document.getId()),
                    AuditLog.AuditAction.UPDATE,
                    Map.of("submitted", String.valueOf(wasSubmitted)),
                    Map.of("submitted", "true",
                            "documentType", document.getDocumentType()),
                    UUID.randomUUID().toString());

            outboxEventService.publish("TPADocument", String.valueOf(document.getId()),
                    "tpa.document.uploaded",
                    Map.of("documentId", document.getId(),
                            "tpaCaseId", document.getTpaCaseId()));

            return toDocumentResponse(document);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload document: {}", e.getMessage(), e);
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload document: " + e.getMessage());
        }
    }

    private TPACaseResponse toResponse(TPACase tpaCase) {
        var documents = documentRepository.findByTenantIdAndTpaCaseId(tpaCase.getTenantId(), tpaCase.getId());
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
        var month = YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        long sequence = caseRepository.nextCaseSequence();
        return String.format(CASE_NUMBER_FORMAT, Long.parseLong(month), sequence);
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
