package com.katixo.hospital.mlc;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Medico-legal case (MLC) register — ED / casualty + records. */
@RestController
@RequestMapping("/api/v1/mlc")
@RequiredArgsConstructor
public class MlcController {

    private final MlcService mlcService;

    public record RegisterRequest(Long patientId, MedicoLegalCase.MlcType mlcType, LocalDateTime incidentAt,
                                  String broughtBy, String policeStation, String firNumber,
                                  boolean broughtDead, String remarks) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody RegisterRequest req) {
        return respond(view(mlcService.register(req.patientId(), req.mlcType(), req.incidentAt(),
                req.broughtBy(), req.policeStation(), req.firNumber(), req.broughtDead(), req.remarks())),
                "MLC registered", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) MedicoLegalCase.CaseStatus status) {
        return respond(mlcService.list(patientId, status).stream().map(MlcController::view).toList(),
                "MLC register", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable Long id) {
        return respond(view(mlcService.get(id)), "MLC", HttpStatus.OK);
    }

    public record CloseRequest(String remarks) {}

    @PutMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(@PathVariable Long id, @RequestBody CloseRequest req) {
        return respond(view(mlcService.close(id, req.remarks())), "MLC closed", HttpStatus.OK);
    }

    private static Map<String, Object> view(MedicoLegalCase c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.getId());
        v.put("mlcNumber", c.getMlcNumber());
        v.put("patientId", c.getPatientId());
        v.put("mlcType", c.getMlcType());
        v.put("incidentAt", c.getIncidentAt() == null ? null : c.getIncidentAt().toString());
        v.put("broughtBy", c.getBroughtBy());
        v.put("policeStation", c.getPoliceStation());
        v.put("firNumber", c.getFirNumber());
        v.put("broughtDead", c.isBroughtDead());
        v.put("caseStatus", c.getCaseStatus());
        v.put("remarks", c.getRemarks());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
