package com.katixo.hospital.inventory;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Schedule H1 / X / NDPS controlled-drug register — statutory report + prescriber capture. */
@RestController
@RequestMapping("/api/v1/pharmacy/controlled-register")
@RequiredArgsConstructor
public class ControlledDrugRegisterController {

    private final ControlledDrugRegisterService registerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Item.DrugSchedule schedule) {
        List<Map<String, Object>> view = registerService.list(from, to, schedule).stream()
                .map(ControlledDrugRegisterController::view).toList();
        return respond(view, "Controlled-drug register", HttpStatus.OK);
    }

    public record PrescriberRequest(String prescriberName, String prescriberAddress) {}

    @PutMapping("/{id}/prescriber")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setPrescriber(
            @PathVariable Long id, @RequestBody PrescriberRequest req) {
        return respond(view(registerService.setPrescriber(id, req.prescriberName(), req.prescriberAddress())),
                "Prescriber recorded", HttpStatus.OK);
    }

    private static Map<String, Object> view(ControlledDrugRegisterEntry e) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", e.getId());
        v.put("entryDate", e.getEntryDate() == null ? null : e.getEntryDate().toString());
        v.put("drugSchedule", e.getDrugSchedule());
        v.put("itemCode", e.getItemCode());
        v.put("itemName", e.getItemName());
        v.put("quantity", e.getQuantity());
        v.put("batchNumber", e.getBatchNumber());
        v.put("patientId", e.getPatientId());
        v.put("saleNumber", e.getSaleNumber());
        v.put("prescriberName", e.getPrescriberName());
        v.put("prescriberAddress", e.getPrescriberAddress());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
