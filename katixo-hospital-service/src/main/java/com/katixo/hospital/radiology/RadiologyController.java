package com.katixo.hospital.radiology;

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

/** Radiology orders + reporting. DOCTOR/ADMIN order + report; LAB_TECH can mark performed; clinical roles view. */
@RestController
@RequestMapping("/api/v1/radiology")
@RequiredArgsConstructor
public class RadiologyController {

    private final RadiologyService radiologyService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private Long referringDoctorId;
        @NotNull
        private RadiologyOrder.Modality modality;
        @NotBlank
        private String studyName;
        private String notes;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> order(@Valid @RequestBody OrderRequest req) {
        RadiologyOrder o = radiologyService.order(req.getPatientId(), req.getReferringDoctorId(),
                req.getModality(), req.getStudyName(), req.getNotes());
        return respond(view(o), "Radiology study ordered", HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(required = false) RadiologyOrder.RadiologyStatus status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(radiologyService.list(status, limit).stream().map(this::view).toList(),
                "Radiology orders", HttpStatus.OK);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(view(radiologyService.get(id)), "Radiology order", HttpStatus.OK);
    }

    @PostMapping("/orders/{id}/performed")
    @PreAuthorize("hasAnyRole('LAB_TECH', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> performed(@PathVariable Long id) {
        return respond(view(radiologyService.markPerformed(id)), "Study marked performed", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportRequest {
        private String findings;
        @NotBlank
        private String impression;
    }

    @PostMapping("/orders/{id}/report")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> report(@PathVariable Long id,
                                                      @Valid @RequestBody ReportRequest req) {
        return respond(view(radiologyService.report(id, req.getFindings(), req.getImpression())),
                "Report filed", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonRequest {
        private String reason;
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancel(@PathVariable Long id,
                                                      @RequestBody(required = false) ReasonRequest req) {
        return respond(view(radiologyService.cancel(id, req == null ? null : req.getReason())),
                "Radiology order cancelled", HttpStatus.OK);
    }

    private Map<String, Object> view(RadiologyOrder o) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", o.getId());
        v.put("orderNumber", o.getOrderNumber());
        v.put("patientId", o.getPatientId());
        v.put("referringDoctorId", o.getReferringDoctorId());
        v.put("modality", o.getModality().name());
        v.put("studyName", o.getStudyName());
        v.put("orderDate", o.getOrderDate() == null ? null : o.getOrderDate().toString());
        v.put("radiologyStatus", o.getRadiologyStatus().name());
        v.put("notes", o.getNotes());
        v.put("findings", o.getFindings());
        v.put("impression", o.getImpression());
        v.put("radiologistId", o.getRadiologistId());
        v.put("reportedAt", o.getReportedAt() == null ? null : o.getReportedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
