package com.katixo.hospital.abdm.terminology;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Clinical terminology map (SNOMED CT / LOINC) — search, lookup, and admin upsert. */
@RestController
@RequestMapping("/api/v1/abdm/terminology")
@RequiredArgsConstructor
public class TerminologyController {

    private final TerminologyService terminologyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> search(
            @RequestParam(required = false, defaultValue = "DIAGNOSIS") String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        List<Map<String, Object>> view = terminologyService.search(category, q, limit).stream()
                .map(TerminologyController::view).toList();
        return respond(view, "Clinical codes", HttpStatus.OK);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lookup(
            @RequestParam String category, @RequestParam String term) {
        Map<String, Object> v = terminologyService.lookup(category, term)
                .map(TerminologyController::view).orElseGet(LinkedHashMap::new);
        return respond(v, "Lookup", HttpStatus.OK);
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class UpsertRequest {
        private String category;
        private String codeSystem;
        private String code;
        private String display;
        private String localTerm;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upsert(@RequestBody UpsertRequest req) {
        ClinicalCode c = terminologyService.upsert(req.getCategory(), req.getCodeSystem(),
                req.getCode(), req.getDisplay(), req.getLocalTerm());
        return respond(view(c), "Saved", HttpStatus.OK);
    }

    private static Map<String, Object> view(ClinicalCode c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.getId());
        v.put("category", c.getCategory());
        v.put("codeSystem", c.getCodeSystem());
        v.put("code", c.getCode());
        v.put("display", c.getDisplay());
        v.put("localTerm", c.getLocalTerm());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
