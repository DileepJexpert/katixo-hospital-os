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
        view.put("reversed", e.isReversed());
        return view;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
