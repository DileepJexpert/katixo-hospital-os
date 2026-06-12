package com.katixo.hospital.billing;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final ErpBillingSyncService erpBillingSyncService;

    // ---------- tariff master ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTariffRequest {
        @NotBlank
        private String serviceCode;
        @NotBlank
        private String serviceName;
        @NotNull
        private TariffMaster.ServiceCategory category;
        @NotNull
        private BigDecimal rate;
    }

    @PostMapping("/tariffs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createTariff(@Valid @RequestBody CreateTariffRequest req) {
        TariffMaster t = billingService.createTariff(req.getServiceCode(), req.getServiceName(),
                req.getCategory(), req.getRate());
        return respond(Map.of("id", t.getId(), "serviceCode", t.getServiceCode(), "rate", t.getRate()),
                "Tariff created", HttpStatus.CREATED);
    }

    @GetMapping("/tariffs")
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTariffs() {
        List<Map<String, Object>> tariffs = billingService.listTariffs().stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "serviceCode", t.getServiceCode(),
                        "serviceName", t.getServiceName(), "category", t.getCategory().name(), "rate", t.getRate()))
                .toList();
        return respond(tariffs, "Tariffs", HttpStatus.OK);
    }

    // ---------- charges ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddChargeRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private HospitalCharge.SourceType sourceType;
        @NotNull
        private Long sourceId;
        @NotBlank
        private String serviceCode;
        private Integer quantity;
    }

    @PostMapping("/charges")
    @PreAuthorize("hasAnyRole('BILLING', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> addCharge(@Valid @RequestBody AddChargeRequest req) {
        HospitalCharge c = billingService.addCharge(req.getPatientId(), req.getSourceType(), req.getSourceId(),
                req.getServiceCode(), req.getQuantity());
        return respond(Map.of("id", c.getId(), "serviceCode", c.getServiceCode(), "quantity", c.getQuantity(),
                        "rate", c.getRate(), "amount", c.getAmount()),
                "Charge added", HttpStatus.CREATED);
    }

    // ---------- bills ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateBillRequest {
        @NotNull
        private HospitalCharge.SourceType sourceType;
        @NotNull
        private Long sourceId;
    }

    @PostMapping("/bills")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> generateBill(@Valid @RequestBody GenerateBillRequest req) {
        PatientBill bill = billingService.generateBill(req.getSourceType(), req.getSourceId());
        return respond(view(bill), "Bill generated", HttpStatus.CREATED);
    }

    @GetMapping("/bills/{id}")
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBill(@PathVariable Long id) {
        return respond(billingService.getConsolidatedBill(id), "Consolidated bill", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountRequest {
        @NotNull
        private BigDecimal amount;
        @NotBlank
        private String reason;
    }

    @PostMapping("/bills/{id}/discount")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> requestDiscount(@PathVariable Long id,
                                                               @Valid @RequestBody DiscountRequest req) {
        PatientBill bill = billingService.requestDiscount(id, req.getAmount(), req.getReason());
        return respond(view(bill), "Discount " + bill.getDiscountStatus(), HttpStatus.OK);
    }

    @PostMapping("/bills/{id}/discount/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approveDiscount(@PathVariable Long id) {
        return respond(view(billingService.approveDiscount(id)), "Discount approved", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErpRefRequest {
        @NotBlank
        private String invoiceNumber;
        @NotNull
        private BigDecimal amount;
        private String invoiceType;
    }

    @PostMapping("/bills/{id}/erp-refs")
    @PreAuthorize("hasAnyRole('BILLING', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> addErpRef(@PathVariable Long id,
                                                         @Valid @RequestBody ErpRefRequest req) {
        BillErpInvoiceRef ref = billingService.addErpInvoiceRef(id, req.getInvoiceNumber(),
                req.getAmount(), req.getInvoiceType());
        return respond(Map.of("id", ref.getId(), "invoiceNumber", ref.getErpInvoiceNumber(),
                "amount", ref.getErpInvoiceAmount()), "ERP invoice linked", HttpStatus.CREATED);
    }

    @PostMapping("/bills/{id}/finalize")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> finalizeBill(@PathVariable Long id) {
        return respond(view(billingService.finalizeBill(id)), "Bill finalized", HttpStatus.OK);
    }

    @GetMapping("/bills/{id}/receipt")
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receipt(@PathVariable Long id) {
        return respond(billingService.getReceipt(id), "Bill receipt", HttpStatus.OK);
    }

    // ---------- payments ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRequest {
        @NotNull
        private BigDecimal amount;
        @NotNull
        private PatientBillPayment.PaymentMode paymentMode;
        private String reference;
        private String notes;
    }

    @PostMapping("/bills/{id}/payments")
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> recordPayment(@PathVariable Long id,
                                                             @Valid @RequestBody PaymentRequest req) {
        PatientBillPayment payment = billingService.recordPayment(id, req.getAmount(),
                req.getPaymentMode(), req.getReference(), req.getNotes());
        return respond(paymentView(payment), "Payment recorded", HttpStatus.CREATED);
    }

    @GetMapping("/bills/{id}/payments")
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listPayments(@PathVariable Long id) {
        return respond(billingService.listPayments(id).stream().map(this::paymentView).toList(),
                "Bill payments", HttpStatus.OK);
    }

    /** Retry the ERP journal for a finalized bill whose automatic sync failed. */
    @PostMapping("/bills/{id}/sync-erp")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> syncBillToErp(@PathVariable Long id) {
        return respond(view(erpBillingSyncService.syncBillJournal(id)), "ERP journal posted", HttpStatus.OK);
    }

    /** Retry the ERP journal for a payment whose automatic sync failed. */
    @PostMapping("/payments/{paymentId}/sync-erp")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> syncPaymentToErp(@PathVariable Long paymentId) {
        return respond(paymentView(erpBillingSyncService.syncPaymentJournal(paymentId)),
                "ERP journal posted", HttpStatus.OK);
    }

    // ---------- helpers ----------

    private Map<String, Object> view(PatientBill b) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", b.getId());
        view.put("billNumber", b.getBillNumber());
        view.put("patientId", b.getPatientId());
        view.put("sourceType", b.getSourceType().name());
        view.put("sourceId", b.getSourceId());
        view.put("chargesTotal", b.getChargesTotal());
        view.put("discountAmount", b.getDiscountAmount());
        view.put("discountStatus", b.getDiscountStatus().name());
        view.put("netAmount", b.getNetAmount());
        view.put("amountPaid", b.getAmountPaid());
        view.put("balanceDue", b.getBalanceDue());
        view.put("billStatus", b.getBillStatus().name());
        view.put("erpSyncStatus", b.getErpSyncStatus().name());
        view.put("erpJournalNumber", b.getErpJournalNumber());
        return view;
    }

    private Map<String, Object> paymentView(PatientBillPayment p) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", p.getId());
        view.put("billId", p.getBillId());
        view.put("amount", p.getAmount());
        view.put("paymentMode", p.getPaymentMode().name());
        view.put("reference", p.getReference());
        view.put("erpSyncStatus", p.getErpSyncStatus().name());
        view.put("erpJournalNumber", p.getErpJournalNumber());
        view.put("erpSyncError", p.getErpSyncError());
        view.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
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
