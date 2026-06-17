package com.katixo.hospital.discharge;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
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
 * Discharge summary endpoints: clinical document that a doctor drafts and signs
 * at or before patient discharge. One summary per IPD admission.
 * PDF: {@code GET /{id}/summary.pdf}.
 */
@RestController
@RequestMapping("/api/v1/discharge/summaries")
@RequiredArgsConstructor
public class DischargeSummaryController {

    private final DischargeSummaryService service;
    private final DischargeSummaryPdfService pdfService;

    // ---- request DTOs ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull
        private Long admissionId;
        private String finalDiagnosis;
        private String courseInHospital;
        private String proceduresPerformed;
        private String conditionAtDischarge;
        private String followUpInstructions;
        private String medicationsAtDischarge;
        private String activityRestrictions;
        private String dietAdvice;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String finalDiagnosis;
        private String courseInHospital;
        private String proceduresPerformed;
        private String conditionAtDischarge;
        private String followUpInstructions;
        private String medicationsAtDischarge;
        private String activityRestrictions;
        private String dietAdvice;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignRequest {
        @NotNull
        private Long doctorId;
        private String doctorName;
    }

    // ---- endpoints ----

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody CreateRequest req) {
        DischargeSummary ds = service.create(
                req.getAdmissionId(),
                req.getFinalDiagnosis(),
                req.getCourseInHospital(),
                req.getProceduresPerformed(),
                req.getConditionAtDischarge(),
                req.getFollowUpInstructions(),
                req.getMedicationsAtDischarge(),
                req.getActivityRestrictions(),
                req.getDietAdvice());
        return respond(view(ds), "Discharge summary created", HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateRequest req) {
        DischargeSummary ds = service.update(id,
                req.getFinalDiagnosis(),
                req.getCourseInHospital(),
                req.getProceduresPerformed(),
                req.getConditionAtDischarge(),
                req.getFollowUpInstructions(),
                req.getMedicationsAtDischarge(),
                req.getActivityRestrictions(),
                req.getDietAdvice());
        return respond(view(ds), "Discharge summary updated", HttpStatus.OK);
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> sign(@PathVariable Long id,
                                                    @Valid @RequestBody SignRequest req) {
        DischargeSummary ds = service.sign(id, req.getDoctorId(), req.getDoctorName());
        return respond(view(ds), "Discharge summary signed", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(view(service.getSummary(id)), "Discharge summary", HttpStatus.OK);
    }

    @GetMapping("/by-admission/{admissionId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> byAdmission(@PathVariable Long admissionId) {
        return respond(view(service.getByAdmission(admissionId)), "Discharge summary", HttpStatus.OK);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(service.list(limit).stream().map(this::view).toList(),
                "Discharge summaries", HttpStatus.OK);
    }

    @GetMapping("/{id}/summary.pdf")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = pdfService.renderPdf(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=discharge-summary-" + id + ".pdf")
                .body(pdf);
    }

    // ---- view ----

    private Map<String, Object> view(DischargeSummary ds) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", ds.getId());
        v.put("summaryNumber", ds.getSummaryNumber());
        v.put("admissionId", ds.getAdmissionId());
        v.put("summaryStatus", ds.getSummaryStatus().name());
        v.put("finalDiagnosis", ds.getFinalDiagnosis());
        v.put("courseInHospital", ds.getCourseInHospital());
        v.put("proceduresPerformed", ds.getProceduresPerformed());
        v.put("conditionAtDischarge", ds.getConditionAtDischarge() == null ? null
                : ds.getConditionAtDischarge().name());
        v.put("followUpInstructions", ds.getFollowUpInstructions());
        v.put("medicationsAtDischarge", ds.getMedicationsAtDischarge());
        v.put("activityRestrictions", ds.getActivityRestrictions());
        v.put("dietAdvice", ds.getDietAdvice());
        v.put("signedByDoctorId", ds.getSignedByDoctorId());
        v.put("signedByDoctorName", ds.getSignedByDoctorName());
        v.put("signedAt", ds.getSignedAt() == null ? null : ds.getSignedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
