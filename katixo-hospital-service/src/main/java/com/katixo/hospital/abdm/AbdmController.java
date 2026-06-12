package com.katixo.hospital.abdm;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.katixo.hospital.abdm.AbdmDtos.AbhaLinkResponse;
import static com.katixo.hospital.abdm.AbdmDtos.LinkAbhaRequest;

/**
 * ABDM endpoints — ABHA linkage for patients (hospital as Health Information Provider).
 */
@RestController
@RequestMapping("/api/v1/abdm/abha")
@Slf4j
@RequiredArgsConstructor
public class AbdmController {

    private final AbdmService abdmService;

    @PostMapping("/link")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<AbhaLinkResponse>> linkAbha(@Valid @RequestBody LinkAbhaRequest request) {
        AbhaLink link = abdmService.linkAbha(request);
        return respond(AbhaLinkResponse.from(link), "ABHA linked to patient", HttpStatus.CREATED);
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<AbhaLinkResponse>> getByPatient(@PathVariable Long patientId) {
        AbhaLink link = abdmService.getActiveLink(patientId);
        return respond(AbhaLinkResponse.from(link), "ABHA link retrieved", HttpStatus.OK);
    }

    @DeleteMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<AbhaLinkResponse>> unlink(@PathVariable Long patientId) {
        AbhaLink link = abdmService.unlinkAbha(patientId);
        return respond(AbhaLinkResponse.from(link), "ABHA unlinked from patient", HttpStatus.OK);
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
