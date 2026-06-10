package com.katixo.hospital.patient;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.katixo.hospital.patient.PatientCreditDtos.*;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/credit")
@Slf4j
@RequiredArgsConstructor
public class PatientCreditController {

    private final PatientCreditService creditService;
    private final PatientCreditTransactionRepository transactionRepository;
    private final org.springframework.data.domain.Sort sort =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("createdAt"));

    @GetMapping
    @PreAuthorize("hasAnyRole('BILLING', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> getCreditAccount(@PathVariable Long patientId) {
        var account = creditService.getAccount(patientId);
        var response = CreditAccountResponse.from(account);

        return respond(response, "Patient credit account retrieved", HttpStatus.OK);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getCreditTransactions(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var context = com.katixo.hospital.tenant.TenantContext.get();
        Page<PatientCreditTransaction> transactions = transactionRepository
                .findByTenantIdAndBranchIdAndPatientIdOrderByCreatedAtDesc(
                        context.getTenantId(),
                        Long.parseLong(context.getBranchId()),
                        patientId,
                        PageRequest.of(page, size));

        Page<TransactionResponse> response = transactions.map(TransactionResponse::from);

        return respond(response, "Credit transactions retrieved", HttpStatus.OK);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> adjustBalance(
            @PathVariable Long patientId,
            @RequestBody AdjustmentRequest request) {

        var account = creditService.adjustBalance(patientId, request.getAmount(), request.getReason());
        var response = CreditAccountResponse.from(account);

        return respond(response, "Credit balance adjusted", HttpStatus.OK);
    }

    @PutMapping("/limit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> setCreditLimit(
            @PathVariable Long patientId,
            @RequestBody SetLimitRequest request) {

        creditService.setCreditLimit(patientId, request.getCreditLimit());
        var account = creditService.getAccount(patientId);
        var response = CreditAccountResponse.from(account);

        return respond(response, "Credit limit updated", HttpStatus.OK);
    }

    @PutMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> updateStatus(
            @PathVariable Long patientId,
            @RequestBody UpdateStatusRequest request) {

        creditService.updateCreditStatus(patientId, request.getStatus());
        var account = creditService.getAccount(patientId);
        var response = CreditAccountResponse.from(account);

        return respond(response, "Credit account status updated", HttpStatus.OK);
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
