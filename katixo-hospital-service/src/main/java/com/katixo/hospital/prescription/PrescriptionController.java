package com.katixo.hospital.prescription;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.katixo.hospital.prescription.PrescriptionDtos.*;

@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionPdfService prescriptionPdfService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> create(@Valid @RequestBody CreateRequest request) {
        Prescription rx = prescriptionService.create(request.getVisitId(), request.getNotes(),
                request.getItems().stream().map(ItemRequest::toEntity).toList(),
                request.isOverrideAllergy(), request.getAllergyOverrideReason());
        return respond(PrescriptionResponse.from(rx), "Prescription created", HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> get(@PathVariable Long id) {
        return respond(PrescriptionResponse.from(prescriptionService.get(id)), "Prescription found", HttpStatus.OK);
    }

    /** Printable A4 prescription (Rx) PDF. */
    @GetMapping("/{id}/print.pdf")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<byte[]> printPdf(@PathVariable Long id) {
        byte[] pdf = prescriptionPdfService.renderPrescriptionPdf(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=prescription-" + id + ".pdf")
                .body(pdf);
    }

    @GetMapping("/visit/{visitId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> byVisit(@PathVariable Long visitId) {
        List<PrescriptionResponse> list = prescriptionService.getLatestByVisit(visitId).stream()
                .map(PrescriptionResponse::from).toList();
        return respond(list, "Prescriptions for visit", HttpStatus.OK);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> history(@PathVariable Long id) {
        List<PrescriptionResponse> list = prescriptionService.getHistory(id).stream()
                .map(PrescriptionResponse::from).toList();
        return respond(list, "Version history", HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> update(@PathVariable Long id,
                                                                    @Valid @RequestBody UpdateRequest request) {
        Prescription rx = prescriptionService.update(id, request.getNotes(),
                request.getItems().stream().map(ItemRequest::toEntity).toList(),
                request.isOverrideAllergy(), request.getAllergyOverrideReason());
        return respond(PrescriptionResponse.from(rx), "Prescription updated", HttpStatus.OK);
    }

    @PutMapping("/{id}/dispense")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> dispense(@PathVariable Long id) {
        return respond(PrescriptionResponse.from(prescriptionService.markDispensed(id)),
                "Prescription dispensed", HttpStatus.OK);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> cancel(@PathVariable Long id) {
        return respond(PrescriptionResponse.from(prescriptionService.cancel(id)),
                "Prescription cancelled", HttpStatus.OK);
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
