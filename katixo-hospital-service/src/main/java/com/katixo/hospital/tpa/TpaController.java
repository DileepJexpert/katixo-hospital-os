package com.katixo.hospital.tpa;

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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** TPA / insurance claims: payer master + case lifecycle (pre-auth → claim → settle) + ageing. */
@RestController
@RequestMapping("/api/v1/tpa")
@RequiredArgsConstructor
public class TpaController {

    private final TpaService tpaService;

    // ---------------- payers ----------------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayerRequest {
        private String name;
        private TpaPayer.PayerType payerType;
        private String contactPerson;
        private String contactPhone;
        private String contactEmail;
    }

    @PostMapping("/payers")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createPayer(@Valid @RequestBody PayerRequest req) {
        return respond(payerView(tpaService.createPayer(req.getName(), req.getPayerType(),
                req.getContactPerson(), req.getContactPhone(), req.getContactEmail())),
                "Payer created", HttpStatus.CREATED);
    }

    @GetMapping("/payers")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listPayers() {
        return respond(tpaService.listPayers().stream().map(this::payerView).toList(),
                "Payers", HttpStatus.OK);
    }

    // ---------------- cases ----------------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseRequest {
        @NotNull
        private Long payerId;
        @NotNull
        private Long patientId;
        private Long admissionId;
        private Long billId;
        private String policyNumber;
        private BigDecimal claimedAmount;
        private String notes;
    }

    @PostMapping("/cases")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createCase(@Valid @RequestBody CaseRequest req) {
        return respond(caseView(tpaService.createCase(req.getPayerId(), req.getPatientId(),
                req.getAdmissionId(), req.getBillId(), req.getPolicyNumber(),
                req.getClaimedAmount(), req.getNotes())), "TPA case created", HttpStatus.CREATED);
    }

    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listCases() {
        return respond(tpaService.listCases().stream().map(this::caseView).toList(),
                "TPA cases", HttpStatus.OK);
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getCase(@PathVariable Long id) {
        Map<String, Object> view = caseView(tpaService.getCase(id));
        view.put("events", tpaService.getEvents(id).stream().map(this::eventView).toList());
        return respond(view, "TPA case", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountRequest {
        private BigDecimal amount;
        private String note;
    }

    @PostMapping("/cases/{id}/query")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> query(@PathVariable Long id,
                                                     @RequestBody(required = false) AmountRequest req) {
        return respond(caseView(tpaService.raiseQuery(id, req == null ? null : req.getNote())),
                "Query raised", HttpStatus.OK);
    }

    @PostMapping("/cases/{id}/approve")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approve(@PathVariable Long id,
                                                       @RequestBody AmountRequest req) {
        return respond(caseView(tpaService.approve(id, req.getAmount())),
                "Approved & receivable recognized", HttpStatus.OK);
    }

    @PostMapping("/cases/{id}/reject")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> reject(@PathVariable Long id,
                                                      @RequestBody(required = false) AmountRequest req) {
        return respond(caseView(tpaService.reject(id, req == null ? null : req.getNote())),
                "Rejected", HttpStatus.OK);
    }

    @PostMapping("/cases/{id}/submit")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> submit(@PathVariable Long id) {
        return respond(caseView(tpaService.submitClaim(id)), "Claim submitted", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettleRequest {
        private BigDecimal receivedAmount;
        private BigDecimal disallowedAmount;
        private boolean fromCash;
    }

    @PostMapping("/cases/{id}/settle")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> settle(@PathVariable Long id,
                                                      @RequestBody SettleRequest req) {
        return respond(caseView(tpaService.settle(id, req.getReceivedAmount(),
                req.getDisallowedAmount(), req.isFromCash())), "Settlement recorded", HttpStatus.OK);
    }

    @GetMapping("/ageing")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> ageing() {
        return respond(tpaService.ageing(), "TPA ageing", HttpStatus.OK);
    }

    // ---------------- views ----------------

    private Map<String, Object> payerView(TpaPayer p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("payerCode", p.getPayerCode());
        v.put("name", p.getName());
        v.put("payerType", p.getPayerType().name());
        v.put("contactPerson", p.getContactPerson());
        v.put("contactPhone", p.getContactPhone());
        v.put("active", p.isActive());
        return v;
    }

    private Map<String, Object> caseView(TpaCase c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.getId());
        v.put("caseNumber", c.getCaseNumber());
        v.put("payerId", c.getPayerId());
        v.put("patientId", c.getPatientId());
        v.put("admissionId", c.getAdmissionId());
        v.put("billId", c.getBillId());
        v.put("policyNumber", c.getPolicyNumber());
        v.put("status", c.getCaseStatus().name());
        v.put("claimedAmount", c.getClaimedAmount());
        v.put("approvedAmount", c.getApprovedAmount());
        v.put("settledAmount", c.getSettledAmount());
        v.put("disallowedAmount", c.getDisallowedAmount());
        v.put("outstanding", c.getApprovedAmount()
                .subtract(c.getSettledAmount()).subtract(c.getDisallowedAmount()));
        v.put("recognitionJournalEntryId", c.getRecognitionJournalEntryId());
        v.put("settlementJournalEntryId", c.getSettlementJournalEntryId());
        return v;
    }

    private Map<String, Object> eventView(TpaCaseEvent e) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("eventType", e.getEventType().name());
        v.put("amount", e.getAmount());
        v.put("note", e.getNote());
        v.put("at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
