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

    private Map<String, Object> saleView(PharmacySale s, List<PharmacySaleLine> lines) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", s.getId());
        view.put("saleNumber", s.getSaleNumber());
        view.put("saleType", s.getSaleType().name());
        view.put("paymentMode", s.getPaymentMode());
        view.put("taxableTotal", s.getTaxableTotal());
        view.put("cgstTotal", s.getCgstTotal());
        view.put("sgstTotal", s.getSgstTotal());
        view.put("igstTotal", s.getIgstTotal());
        view.put("grandTotal", s.getGrandTotal());
        view.put("journalEntryId", s.getJournalEntryId());
        view.put("lines", lines.stream().map(l -> Map.of(
                "itemCode", l.getItemCode(),
                "itemName", l.getItemName(),
                "quantity", l.getQuantity(),
                "mrp", l.getMrp(),
                "gstRate", l.getGstRate(),
                "lineTotal", l.getLineTotal())).toList());
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
