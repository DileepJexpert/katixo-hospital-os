package com.katixo.hospital.clinical;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * EMR / CPOE endpoints: open encounters, record versioned SOAP notes, and place
 * clinical orders through the CDS gate.
 */
@RestController
@RequestMapping("/api/v1/clinical")
@RequiredArgsConstructor
public class ClinicalController {

    private final ClinicalService clinicalService;
    private final CpoeService cpoeService;
    private final EncounterPdfService encounterPdfService;

    @GetMapping(value = "/encounters/{id}/summary.pdf",
            produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<byte[]> summaryPdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=encounter-" + id + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(encounterPdfService.renderSummaryPdf(id));
    }

    // ---- encounters ----
    public record OpenEncounterRequest(Long patientId, Encounter.EncounterType encounterType,
                                       Encounter.SourceType sourceType, Long sourceId,
                                       Long attendingDoctorId, String chiefComplaint) {}

    @PostMapping("/encounters")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> open(@RequestBody OpenEncounterRequest req) {
        Encounter e = clinicalService.openEncounter(req.patientId(), req.encounterType(),
                req.sourceType(), req.sourceId(), req.attendingDoctorId(), req.chiefComplaint());
        return respond(e, "Encounter opened", HttpStatus.CREATED);
    }

    @GetMapping("/encounters/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(clinicalService.getEncounter(id), "Encounter", HttpStatus.OK);
    }

    @PostMapping("/encounters/{id}/close")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> close(@PathVariable Long id) {
        return respond(clinicalService.closeEncounter(id), "Encounter closed", HttpStatus.OK);
    }

    @GetMapping("/patients/{patientId}/encounters")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> forPatient(@PathVariable Long patientId) {
        return respond(clinicalService.listForPatient(patientId), "Encounters", HttpStatus.OK);
    }

    // ---- notes ----
    public record NoteRequest(ClinicalNote.NoteType noteType, String subjective, String objective,
                              String assessment, String plan, String authorName) {}

    @PostMapping("/encounters/{id}/notes")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> addNote(@PathVariable Long id, @RequestBody NoteRequest req) {
        ClinicalNote n = clinicalService.addNote(id, req.noteType(), req.subjective(), req.objective(),
                req.assessment(), req.plan(), req.authorName());
        return respond(n, "Note saved", HttpStatus.CREATED);
    }

    @GetMapping("/encounters/{id}/notes")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listNotes(@PathVariable Long id) {
        return respond(clinicalService.listNotes(id), "Notes", HttpStatus.OK);
    }

    // ---- CPOE orders ----
    public record OrderRequest(ClinicalOrder.OrderType orderType, String code, String name,
                               ClinicalOrder.Priority priority, String instructions, String overrideReason) {}

    @PostMapping("/encounters/{id}/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> placeOrder(@PathVariable Long id, @RequestBody OrderRequest req) {
        CpoeService.PlaceResult r = cpoeService.placeOrder(id, req.orderType(), req.code(), req.name(),
                req.priority(), req.instructions(), req.overrideReason());
        return respond(Map.of("order", r.order(), "alerts", r.alerts()), "Order placed", HttpStatus.CREATED);
    }

    @GetMapping("/encounters/{id}/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listOrders(@PathVariable Long id) {
        return respond(cpoeService.listOrders(id), "Orders", HttpStatus.OK);
    }

    public record StatusRequest(ClinicalOrder.OrderStatus status) {}

    @PutMapping("/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECH', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> updateStatus(@PathVariable Long orderId, @RequestBody StatusRequest req) {
        return respond(cpoeService.updateStatus(orderId, req.status()), "Order updated", HttpStatus.OK);
    }

    private ResponseEntity<ApiResponse<Object>> respond(Object data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<Object>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
