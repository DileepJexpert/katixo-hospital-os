package com.katixo.hospital.certificate;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Medical certificates: a template master (ADMIN) and issued certificates
 * (DOCTOR/ADMIN issue + revoke; clinical roles view). PDF per certificate.
 * No accounting — medico-legal data.
 */
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final CertificatePdfService certificatePdfService;

    // ---- templates ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String title;
        @NotNull
        private CertificateTemplate.CertificateType certificateType;
        @NotBlank
        private String bodyText;
        private String language;
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createTemplate(@Valid @RequestBody TemplateRequest req) {
        return respond(templateView(certificateService.createTemplate(req.getCode(), req.getTitle(),
                req.getCertificateType(), req.getBodyText(), req.getLanguage())),
                "Certificate template created", HttpStatus.CREATED);
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<Object>> listTemplates() {
        return respond(certificateService.listTemplates().stream().map(this::templateView).toList(),
                "Certificate templates", HttpStatus.OK);
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<Object>> getTemplate(@PathVariable Long id) {
        return respond(templateView(certificateService.getTemplate(id)), "Certificate template", HttpStatus.OK);
    }

    // ---- certificates ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueRequest {
        @NotNull
        private Long patientId;
        /** When set, the title/type/body are taken from this template (snapshotted). */
        private Long templateId;
        private CertificateTemplate.CertificateType certificateType;
        private String title;
        private String bodyText;
        private Long issuingDoctorId;
        private String issuingDoctorName;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate issueDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate validFrom;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate validTo;
        private String remarks;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> issue(@Valid @RequestBody IssueRequest req) {
        Certificate c = certificateService.issue(req.getPatientId(), req.getTemplateId(),
                req.getCertificateType(), req.getTitle(), req.getBodyText(),
                req.getIssuingDoctorId(), req.getIssuingDoctorName(),
                req.getIssueDate(), req.getValidFrom(), req.getValidTo(), req.getRemarks());
        return respond(certificateView(c), "Certificate issued", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Certificate.CertificateStatus status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(certificateService.listCertificates(patientId, status, limit).stream()
                        .map(this::certificateView).toList(),
                "Certificates", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(certificateView(certificateService.getCertificate(id)), "Certificate", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevokeRequest {
        @NotBlank
        private String reason;
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> revoke(@PathVariable Long id,
                                                      @Valid @RequestBody RevokeRequest req) {
        return respond(certificateView(certificateService.revoke(id, req.getReason())),
                "Certificate revoked", HttpStatus.OK);
    }

    @GetMapping("/{id}/certificate.pdf")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = certificatePdfService.renderPdf(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=certificate-" + id + ".pdf")
                .body(pdf);
    }

    // ---- views ----

    private Map<String, Object> templateView(CertificateTemplate t) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", t.getId());
        v.put("code", t.getCode());
        v.put("title", t.getTitle());
        v.put("certificateType", t.getCertificateType().name());
        v.put("bodyText", t.getBodyText());
        v.put("language", t.getLanguage());
        v.put("active", t.isActive());
        return v;
    }

    private Map<String, Object> certificateView(Certificate c) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", c.getId());
        v.put("certificateNumber", c.getCertificateNumber());
        v.put("patientId", c.getPatientId());
        v.put("templateId", c.getTemplateId());
        v.put("certificateType", c.getCertificateType().name());
        v.put("title", c.getTitle());
        v.put("bodyText", c.getBodyText());
        v.put("issuingDoctorId", c.getIssuingDoctorId());
        v.put("issuingDoctorName", c.getIssuingDoctorName());
        v.put("issueDate", c.getIssueDate() == null ? null : c.getIssueDate().toString());
        v.put("validFrom", c.getValidFrom() == null ? null : c.getValidFrom().toString());
        v.put("validTo", c.getValidTo() == null ? null : c.getValidTo().toString());
        v.put("remarks", c.getRemarks());
        v.put("certificateStatus", c.getCertificateStatus().name());
        v.put("revokedReason", c.getRevokedReason());
        v.put("revokedAt", c.getRevokedAt() == null ? null : c.getRevokedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
