package com.katixo.hospital.emar;

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

/** Electronic Medication Administration Record (eMAR). */
@RestController
@RequestMapping("/api/v1/emar")
@RequiredArgsConstructor
public class EmarController {

    private final EmarService emarService;

    public record AdministerRequest(Long patientId, Long admissionId, Long prescriptionId,
                                    String medicineCode, String medicineName, String dose, String route,
                                    LocalDateTime scheduledAt, MedicationAdministration.AdminStatus adminStatus,
                                    String reason, boolean rightsConfirmed, String notes) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> administer(@RequestBody AdministerRequest req) {
        MedicationAdministration m = emarService.record(req.patientId(), req.admissionId(), req.prescriptionId(),
                req.medicineCode(), req.medicineName(), req.dose(), req.route(), req.scheduledAt(),
                req.adminStatus(), req.reason(), req.rightsConfirmed(), req.notes());
        return respond(view(m), "Administration recorded", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long admissionId) {
        return respond(emarService.list(patientId, admissionId).stream().map(EmarController::view).toList(),
                "Medication administration record", HttpStatus.OK);
    }

    private static Map<String, Object> view(MedicationAdministration m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("patientId", m.getPatientId());
        v.put("admissionId", m.getAdmissionId());
        v.put("medicineName", m.getMedicineName());
        v.put("dose", m.getDose());
        v.put("route", m.getRoute());
        v.put("scheduledAt", m.getScheduledAt() == null ? null : m.getScheduledAt().toString());
        v.put("administeredAt", m.getAdministeredAt() == null ? null : m.getAdministeredAt().toString());
        v.put("administeredBy", m.getAdministeredBy());
        v.put("adminStatus", m.getAdminStatus());
        v.put("reason", m.getReason());
        v.put("rightsConfirmed", m.isRightsConfirmed());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
