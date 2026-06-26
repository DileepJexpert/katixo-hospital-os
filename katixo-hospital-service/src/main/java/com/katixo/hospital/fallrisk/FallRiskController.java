package com.katixo.hospital.fallrisk;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fall-risk assessment (NABH COP 16C). */
@RestController
@RequestMapping("/api/v1/fall-risk")
@RequiredArgsConstructor
public class FallRiskController {

    private final FallRiskService fallRiskService;

    public record AssessRequest(Long patientId, Long admissionId, FallRiskAssessment.Scale scale,
                                Integer score, String factors, String notes) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assess(@RequestBody AssessRequest req) {
        return respond(view(fallRiskService.assess(req.patientId(), req.admissionId(), req.scale(),
                req.score(), req.factors(), req.notes())), "Fall-risk assessment recorded", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long admissionId) {
        return respond(fallRiskService.list(patientId, admissionId).stream().map(FallRiskController::view).toList(),
                "Fall-risk assessments", HttpStatus.OK);
    }

    private static Map<String, Object> view(FallRiskAssessment a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId());
        v.put("patientId", a.getPatientId());
        v.put("admissionId", a.getAdmissionId());
        v.put("scale", a.getScale());
        v.put("score", a.getScore());
        v.put("riskLevel", a.getRiskLevel());
        v.put("assessedAt", a.getAssessedAt() == null ? null : a.getAssessedAt().toString());
        v.put("assessedBy", a.getAssessedBy());
        v.put("factors", a.getFactors());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
