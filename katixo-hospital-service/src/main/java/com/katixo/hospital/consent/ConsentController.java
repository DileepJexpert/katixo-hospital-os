package com.katixo.hospital.consent;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Patient consent: a template master (ADMIN) and captured consent records
 * (clinical staff capture; DOCTOR/ADMIN withdraw). No accounting — medico-legal data.
 */
@RestController
@RequestMapping("/api/v1/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

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
        private ConsentTemplate.ConsentType consentType;
        @NotBlank
        private String bodyText;
        private String language;
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createTemplate(@Valid @RequestBody TemplateRequest req) {
        return respond(templateView(consentService.createTemplate(req.getCode(), req.getTitle(),
                req.getConsentType(), req.getBodyText(), req.getLanguage())),
                "Consent template created", HttpStatus.CREATED);
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<Object>> listTemplates() {
        return respond(consentService.listTemplates().stream().map(this::templateView).toList(),
                "Consent templates", HttpStatus.OK);
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<Object>> getTemplate(@PathVariable Long id) {
        return respond(templateView(consentService.getTemplate(id)), "Consent template", HttpStatus.OK);
    }

    // ---- records ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaptureRequest {
        @NotNull
        private Long patientId;
        /** When set, the title/type/body are taken from this template (snapshotted). */
        private Long templateId;
        private ConsentTemplate.ConsentType consentType;
        private String title;
        private String bodyText;
        private ConsentRecord.SourceType sourceType;
        private Long sourceId;
        @NotNull
        private ConsentRecord.Signatory signatory;
        @NotBlank
        private String signatoryName;
        private String relationToPatient;
        private String witnessName;
        private String language;
        /** GIVEN (default) or REFUSED. */
        private ConsentRecord.ConsentStatus consentStatus;
    }

    @PostMapping("/records")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> capture(@Valid @RequestBody CaptureRequest req) {
        ConsentRecord r = consentService.capture(req.getPatientId(), req.getTemplateId(),
                req.getConsentType(), req.getTitle(), req.getBodyText(),
                req.getSourceType(), req.getSourceId(), req.getSignatory(), req.getSignatoryName(),
                req.getRelationToPatient(), req.getWitnessName(), req.getLanguage(), req.getConsentStatus());
        return respond(recordView(r), "Consent captured", HttpStatus.CREATED);
    }

    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listRecords(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) ConsentRecord.ConsentStatus status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(consentService.listRecords(patientId, status, limit).stream().map(this::recordView).toList(),
                "Consent records", HttpStatus.OK);
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getRecord(@PathVariable Long id) {
        return respond(recordView(consentService.getRecord(id)), "Consent record", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawRequest {
        @NotBlank
        private String reason;
    }

    @PostMapping("/records/{id}/withdraw")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> withdraw(@PathVariable Long id,
                                                        @Valid @RequestBody WithdrawRequest req) {
        return respond(recordView(consentService.withdraw(id, req.getReason())),
                "Consent withdrawn", HttpStatus.OK);
    }

    // ---- views ----

    private Map<String, Object> templateView(ConsentTemplate t) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", t.getId());
        v.put("code", t.getCode());
        v.put("title", t.getTitle());
        v.put("consentType", t.getConsentType().name());
        v.put("bodyText", t.getBodyText());
        v.put("language", t.getLanguage());
        v.put("active", t.isActive());
        return v;
    }

    private Map<String, Object> recordView(ConsentRecord r) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("recordNumber", r.getRecordNumber());
        v.put("patientId", r.getPatientId());
        v.put("templateId", r.getTemplateId());
        v.put("consentType", r.getConsentType().name());
        v.put("title", r.getTitle());
        v.put("bodyText", r.getBodyText());
        v.put("sourceType", r.getSourceType() == null ? null : r.getSourceType().name());
        v.put("sourceId", r.getSourceId());
        v.put("signatory", r.getSignatory().name());
        v.put("signatoryName", r.getSignatoryName());
        v.put("relationToPatient", r.getRelationToPatient());
        v.put("witnessName", r.getWitnessName());
        v.put("language", r.getLanguage());
        v.put("consentStatus", r.getConsentStatus().name());
        v.put("givenAt", r.getGivenAt() == null ? null : r.getGivenAt().toString());
        v.put("withdrawnReason", r.getWithdrawnReason());
        v.put("withdrawnAt", r.getWithdrawnAt() == null ? null : r.getWithdrawnAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
