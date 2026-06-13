package com.katixo.hospital.expense;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ExpenseVoucherPdfService voucherPdfService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordExpenseRequest {
        private LocalDate expenseDate;
        @NotNull
        private Expense.ExpenseCategory category;
        private String payeeName;
        @NotNull
        private BigDecimal amount;
        @NotNull
        private Expense.PaymentMode paymentMode;
        private String reference;
        private String notes;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> record(@Valid @RequestBody RecordExpenseRequest req) {
        Expense e = expenseService.record(req.getExpenseDate(), req.getCategory(), req.getPayeeName(),
                req.getAmount(), req.getPaymentMode(), req.getReference(), req.getNotes());
        return respond(view(e), "Expense recorded", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return respond(expenseService.list(from, to).stream().map(this::view).toList(), "Expenses", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayExpenseRequest {
        @NotNull
        private Expense.PaymentMode mode;
        private LocalDate paidDate;
        private String reference;
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> pay(@PathVariable Long id,
                                                   @Valid @RequestBody PayExpenseRequest req) {
        return respond(view(expenseService.pay(id, req.getMode(), req.getPaidDate(), req.getReference())),
                "Expense paid", HttpStatus.OK);
    }

    @GetMapping("/{id}/voucher.pdf")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<byte[]> voucherPdf(@PathVariable Long id) {
        byte[] pdf = voucherPdfService.renderVoucherPdf(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=expense-voucher-" + id + ".pdf")
                .body(pdf);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonRequest {
        private String reason;
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> reverse(@PathVariable Long id,
                                                       @RequestBody(required = false) ReasonRequest req) {
        return respond(view(expenseService.reverse(id, req == null ? null : req.getReason())),
                "Expense reversed", HttpStatus.OK);
    }

    private Map<String, Object> view(Expense e) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", e.getId());
        view.put("expenseNumber", e.getExpenseNumber());
        view.put("expenseDate", e.getExpenseDate().toString());
        view.put("category", e.getCategory().name());
        view.put("payeeName", e.getPayeeName());
        view.put("amount", e.getAmount());
        view.put("paymentMode", e.getPaymentMode().name());
        view.put("journalNumber", e.getJournalNumber());
        view.put("paid", e.isPaid());
        view.put("paidDate", e.getPaidDate() == null ? null : e.getPaidDate().toString());
        view.put("paidMode", e.getPaidMode() == null ? null : e.getPaidMode().name());
        view.put("paidJournalNumber", e.getPaidJournalNumber());
        view.put("reversed", e.isReversed());
        return view;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
