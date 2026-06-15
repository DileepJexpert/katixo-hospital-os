package com.katixo.hospital.billing;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Patient credit: read available credit and set the per-patient credit limit. */
@RestController
@RequestMapping("/api/v1/billing/patients")
@RequiredArgsConstructor
public class PatientCreditController {

    private final PatientCreditService patientCreditService;

    @GetMapping("/{patientId}/credit")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> credit(@PathVariable Long patientId) {
        return respond(patientCreditService.credit(patientId), "Patient credit", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitRequest {
        @NotNull
        private BigDecimal limit;
    }

    @PutMapping("/{patientId}/credit-limit")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setLimit(@PathVariable Long patientId,
                                                                     @RequestBody LimitRequest req) {
        return respond(patientCreditService.setCreditLimit(patientId, req.getLimit()),
                "Credit limit updated", HttpStatus.OK);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> respond(Map<String, Object> data, String message,
                                                                     HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<Map<String, Object>>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
