package com.katixo.hospital.inventory;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

/**
 * Pharmacy sales surface. The OPD dispense and IPD indent flows create sales
 * internally; this controller exposes the **OTC quick sale** — a walk-in
 * counter sale with no UHID/patient — plus read access to any sale.
 */
@RestController
@RequestMapping("/api/v1/pharmacy-sales")
@RequiredArgsConstructor
public class PharmacySaleController {

    private final PharmacySaleService pharmacySaleService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaleLine {
        @NotNull
        private String itemCode;
        @NotNull
        private BigDecimal quantity;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtcSaleRequest {
        /** CASH/CARD/UPI/CHEQUE/BANK_TRANSFER — defaults to CASH. */
        private String paymentMode;
        private boolean interState;
        @NotEmpty
        private List<SaleLine> lines;
    }

    /** Walk-in counter sale: cash, no patient. FEFO-issues stock + posts GST/COGS journal. */
    @PostMapping("/otc")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> otcSale(@Valid @RequestBody OtcSaleRequest req) {
        List<PharmacySaleService.SaleLineInput> lines = req.getLines().stream()
                .map(l -> new PharmacySaleService.SaleLineInput(l.getItemCode(), l.getQuantity()))
                .toList();
        PharmacySale sale = pharmacySaleService.createSale(new PharmacySaleService.SaleRequest(
                PharmacySale.SaleType.CASH, null, "OTC", null,
                req.getPaymentMode() == null ? "CASH" : req.getPaymentMode(), req.isInterState(), lines));
        return respond(saleView(sale, pharmacySaleService.getLines(sale.getId())), "OTC sale recorded",
                HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getSale(@PathVariable Long id) {
        PharmacySale sale = pharmacySaleService.getSale(id);
        return respond(saleView(sale, pharmacySaleService.getLines(id)), "Sale", HttpStatus.OK);
    }

    /** Recent sales (newest first) — the dispensed-history list. */
    @GetMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> recent(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> sales = pharmacySaleService.listRecentSales(limit).stream()
                .map(this::saleSummary).toList();
        return respond(sales, "Recent sales", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnLine {
        @NotNull
        private String itemCode;
        @NotNull
        private BigDecimal quantity;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnRequest {
        private String reason;
        @NotEmpty
        private List<ReturnLine> lines;
    }

    /** Partial return of unused medicines against a sale (IPD return / OTC return). */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> returnItems(@PathVariable Long id,
                                                           @Valid @RequestBody ReturnRequest req) {
        List<PharmacySaleService.ReturnLineInput> lines = req.getLines().stream()
                .map(l -> new PharmacySaleService.ReturnLineInput(l.getItemCode(), l.getQuantity()))
                .toList();
        PharmacySale sale = pharmacySaleService.returnItems(id, lines, req.getReason());
        return respond(saleView(sale, pharmacySaleService.getLines(id)), "Items returned", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonRequest {
        private String reason;
    }

    /** Return/reverse a sale: restores stock to its batches and reverses the journal. */
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> reverseSale(@PathVariable Long id,
                                                           @RequestBody(required = false) ReasonRequest req) {
        String reason = req == null ? null : req.getReason();
        PharmacySale sale = pharmacySaleService.reverseSale(id, reason);
        return respond(saleView(sale, pharmacySaleService.getLines(id)), "Sale reversed", HttpStatus.OK);
    }

    /** Compact header for the recent-sales list. */
    private Map<String, Object> saleSummary(PharmacySale s) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", s.getId());
        v.put("saleNumber", s.getSaleNumber());
        v.put("saleType", s.getSaleType().name());
        v.put("saleDate", s.getSaleDate() == null ? null : s.getSaleDate().toString());
        v.put("paymentMode", s.getPaymentMode());
        v.put("grandTotal", s.getGrandTotal());
        v.put("reversed", s.isReversed());
        v.put("patientId", s.getPatientId());
        v.put("referenceType", s.getReferenceType());
        v.put("referenceId", s.getReferenceId());
        return v;
    }

    private Map<String, Object> saleView(PharmacySale s, List<PharmacySaleLine> lines) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", s.getId());
        view.put("saleNumber", s.getSaleNumber());
        view.put("saleType", s.getSaleType().name());
        view.put("saleDate", s.getSaleDate() == null ? null : s.getSaleDate().toString());
        view.put("paymentMode", s.getPaymentMode());
        view.put("patientId", s.getPatientId());
        view.put("referenceType", s.getReferenceType());
        view.put("referenceId", s.getReferenceId());
        view.put("taxableTotal", s.getTaxableTotal());
        view.put("cgstTotal", s.getCgstTotal());
        view.put("sgstTotal", s.getSgstTotal());
        view.put("igstTotal", s.getIgstTotal());
        view.put("grandTotal", s.getGrandTotal());
        view.put("reversed", s.isReversed());
        view.put("journalEntryId", s.getJournalEntryId());
        view.put("lines", lines.stream().map(l -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("itemCode", l.getItemCode());
            m.put("itemName", l.getItemName());
            m.put("quantity", l.getQuantity());
            m.put("returnedQuantity", l.getReturnedQuantity());
            m.put("mrp", l.getMrp());
            m.put("gstRate", l.getGstRate());
            m.put("lineTotal", l.getLineTotal());
            return m;
        }).toList());
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
