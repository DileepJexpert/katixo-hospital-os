package com.katixo.hospital.nabh;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * NABH quality + incidents. Quality indicators are managed by ADMIN; incidents
 * can be raised by any clinical role and reviewed/closed by ADMIN.
 */
@RestController
@RequestMapping("/api/v1/nabh")
@RequiredArgsConstructor
public class NabhController {

    private final NabhService nabhService;

    // ---- quality indicators ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private String category;
        private String unit;
        private BigDecimal targetValue;
    }

    @PostMapping("/indicators")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createIndicator(@Valid @RequestBody IndicatorRequest req) {
        return respond(indicatorView(nabhService.createIndicator(req.getCode(), req.getName(),
                req.getCategory(), req.getUnit(), req.getTargetValue())), "Indicator created", HttpStatus.CREATED);
    }

    @GetMapping("/indicators")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<Object>> listIndicators() {
        return respond(nabhService.listIndicators().stream().map(this::indicatorView).toList(),
                "Quality indicators", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadingRequest {
        @NotBlank
        private String period;
        @NotNull
        private BigDecimal value;
        private BigDecimal numerator;
        private BigDecimal denominator;
        private String notes;
    }

    @PostMapping("/indicators/{id}/readings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> recordReading(@PathVariable Long id,
                                                             @Valid @RequestBody ReadingRequest req) {
        return respond(readingView(nabhService.recordReading(id, req.getPeriod(), req.getValue(),
                req.getNumerator(), req.getDenominator(), req.getNotes())), "Reading recorded", HttpStatus.CREATED);
    }

    @GetMapping("/indicators/{id}/readings")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<Object>> listReadings(@PathVariable Long id) {
        return respond(nabhService.listReadings(id).stream().map(this::readingView).toList(),
                "Readings", HttpStatus.OK);
    }

    // ---- incidents ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentRequest {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate incidentDate;
        @NotNull
        private IncidentReport.IncidentType incidentType;
        @NotNull
        private IncidentReport.Severity severity;
        private String location;
        private Long patientId;
        @NotBlank
        private String description;
        private String immediateAction;
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'PHARMACIST', 'LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> reportIncident(@Valid @RequestBody IncidentRequest req) {
        IncidentReport i = nabhService.reportIncident(req.getIncidentDate(), req.getIncidentType(),
                req.getSeverity(), req.getLocation(), req.getPatientId(), req.getDescription(),
                req.getImmediateAction());
        return respond(incidentView(i), "Incident reported", HttpStatus.CREATED);
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'PHARMACIST', 'LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listIncidents(
            @RequestParam(required = false) IncidentReport.IncidentStatus status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(nabhService.listIncidents(status, limit).stream().map(this::incidentView).toList(),
                "Incidents", HttpStatus.OK);
    }

    @PostMapping("/incidents/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> review(@PathVariable Long id) {
        return respond(incidentView(nabhService.startReview(id)), "Incident under review", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CloseRequest {
        @NotBlank
        private String rootCause;
        @NotBlank
        private String correctiveAction;
    }

    @PostMapping("/incidents/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> close(@PathVariable Long id,
                                                     @Valid @RequestBody CloseRequest req) {
        return respond(incidentView(nabhService.closeIncident(id, req.getRootCause(), req.getCorrectiveAction())),
                "Incident closed", HttpStatus.OK);
    }

    private Map<String, Object> indicatorView(QualityIndicator q) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", q.getId());
        v.put("code", q.getCode());
        v.put("name", q.getName());
        v.put("category", q.getCategory());
        v.put("unit", q.getUnit());
        v.put("targetValue", q.getTargetValue());
        v.put("active", q.isActive());
        return v;
    }

    private Map<String, Object> readingView(QualityIndicatorReading r) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("indicatorId", r.getIndicatorId());
        v.put("period", r.getPeriod());
        v.put("value", r.getValue());
        v.put("numerator", r.getNumerator());
        v.put("denominator", r.getDenominator());
        v.put("notes", r.getNotes());
        return v;
    }

    private Map<String, Object> incidentView(IncidentReport i) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", i.getId());
        v.put("reportNumber", i.getReportNumber());
        v.put("incidentDate", i.getIncidentDate() == null ? null : i.getIncidentDate().toString());
        v.put("incidentType", i.getIncidentType().name());
        v.put("severity", i.getSeverity().name());
        v.put("location", i.getLocation());
        v.put("patientId", i.getPatientId());
        v.put("description", i.getDescription());
        v.put("immediateAction", i.getImmediateAction());
        v.put("incidentStatus", i.getIncidentStatus().name());
        v.put("rootCause", i.getRootCause());
        v.put("correctiveAction", i.getCorrectiveAction());
        v.put("closedAt", i.getClosedAt() == null ? null : i.getClosedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
