package com.katixo.hospital.nursing;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Nursing vitals charting. Nurses/doctors record vital-sign readings for a
 * patient; clinical roles view the trend. Purely clinical data — no accounting.
 * Lives under the shared {@code /api/v1/nursing} base alongside indents.
 */
@RestController
@RequestMapping("/api/v1/nursing")
@RequiredArgsConstructor
public class NursingVitalController {

    private final NursingVitalService vitalService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull
        private Long patientId;
        private Long admissionId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime recordedAt;
        private BigDecimal temperatureCelsius;
        private Integer pulseBpm;
        private Integer respiratoryRate;
        private Integer systolicBp;
        private Integer diastolicBp;
        private Integer spo2;
        private Integer bloodSugarMgDl;
        private BigDecimal weightKg;
        private Integer painScore;
        private String notes;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Long admissionId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime recordedAt;
        private BigDecimal temperatureCelsius;
        private Integer pulseBpm;
        private Integer respiratoryRate;
        private Integer systolicBp;
        private Integer diastolicBp;
        private Integer spo2;
        private Integer bloodSugarMgDl;
        private BigDecimal weightKg;
        private Integer painScore;
        private String notes;
    }

    @PostMapping("/vitals")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> record(@Valid @RequestBody CreateRequest req) {
        NursingVital v = vitalService.record(req.getPatientId(), req.getAdmissionId(), req.getRecordedAt(),
                req.getTemperatureCelsius(), req.getPulseBpm(), req.getRespiratoryRate(),
                req.getSystolicBp(), req.getDiastolicBp(), req.getSpo2(),
                req.getBloodSugarMgDl(), req.getWeightKg(), req.getPainScore(), req.getNotes());
        return respond(view(v), "Vitals recorded", HttpStatus.CREATED);
    }

    @PutMapping("/vitals/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateRequest req) {
        NursingVital v = vitalService.update(id, req.getAdmissionId(), req.getRecordedAt(),
                req.getTemperatureCelsius(), req.getPulseBpm(), req.getRespiratoryRate(),
                req.getSystolicBp(), req.getDiastolicBp(), req.getSpo2(),
                req.getBloodSugarMgDl(), req.getWeightKg(), req.getPainScore(), req.getNotes());
        return respond(view(v), "Vitals updated", HttpStatus.OK);
    }

    @DeleteMapping("/vitals/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long id) {
        vitalService.delete(id);
        return respond(Map.of("id", id), "Vitals deleted", HttpStatus.OK);
    }

    @GetMapping("/vitals")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long admissionId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(vitalService.list(patientId, admissionId, limit).stream().map(this::view).toList(),
                "Vitals", HttpStatus.OK);
    }

    @GetMapping("/vitals/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(view(vitalService.get(id)), "Vital", HttpStatus.OK);
    }

    // ---------- helpers ----------

    private Map<String, Object> view(NursingVital v) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", v.getId());
        view.put("patientId", v.getPatientId());
        view.put("admissionId", v.getAdmissionId());
        view.put("recordedAt", v.getRecordedAt() == null ? null : v.getRecordedAt().toString());
        view.put("temperatureCelsius", v.getTemperatureCelsius());
        view.put("pulseBpm", v.getPulseBpm());
        view.put("respiratoryRate", v.getRespiratoryRate());
        view.put("systolicBp", v.getSystolicBp());
        view.put("diastolicBp", v.getDiastolicBp());
        view.put("spo2", v.getSpo2());
        view.put("bloodSugarMgDl", v.getBloodSugarMgDl());
        view.put("weightKg", v.getWeightKg());
        view.put("painScore", v.getPainScore());
        view.put("notes", v.getNotes());
        view.put("recordedByName", v.getRecordedByName());
        return view;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true)
                .status(status.value())
                .message(message)
                .correlationId(UUID.randomUUID())
                .data(data)
                .build());
    }
}
