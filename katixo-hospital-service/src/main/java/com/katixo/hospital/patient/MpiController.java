package com.katixo.hospital.patient;

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

/** Master Patient Index — duplicate detection + merge (NABH COP 1B). */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class MpiController {

    private final MpiService mpiService;

    @GetMapping("/{id}/duplicates")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> duplicates(@PathVariable Long id) {
        return respond(mpiService.findDuplicates(id).stream().map(MpiController::view).toList(),
                "Duplicate candidates", HttpStatus.OK);
    }

    public record MergeRequest(Long survivorId, Long duplicateId, String reason) {}

    @PostMapping("/merge")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> merge(@RequestBody MergeRequest req) {
        return respond(view(mpiService.merge(req.survivorId(), req.duplicateId(), req.reason())),
                "Patients merged", HttpStatus.OK);
    }

    private static Map<String, Object> view(Patient p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("uhid", p.getUhid());
        v.put("name", p.getFullName());
        v.put("mobile", p.getMobile());
        v.put("dateOfBirth", p.getDateOfBirth() == null ? null : p.getDateOfBirth().toString());
        v.put("gender", p.getGender());
        v.put("status", p.getStatus());
        v.put("mergedIntoId", p.getMergedIntoId());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
