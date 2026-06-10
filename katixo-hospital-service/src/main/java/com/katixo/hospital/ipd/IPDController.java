package com.katixo.hospital.ipd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ipd")
@RequiredArgsConstructor
public class IPDController {

    private final IPDService ipdService;

    // ---------- masters ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateWardRequest {
        @NotBlank
        private String name;
        @NotNull
        private Ward.WardType wardType;
    }

    @PostMapping("/wards")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createWard(@Valid @RequestBody CreateWardRequest req) {
        Ward w = ipdService.createWard(req.getName(), req.getWardType());
        return respond(java.util.Map.of("id", w.getId(), "name", w.getName(), "wardType", w.getWardType()),
                "Ward created", HttpStatus.CREATED);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRoomRequest {
        @NotNull
        private Long wardId;
        @NotBlank
        private String roomNumber;
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createRoom(@Valid @RequestBody CreateRoomRequest req) {
        Room r = ipdService.createRoom(req.getWardId(), req.getRoomNumber());
        return respond(java.util.Map.of("id", r.getId(), "wardId", r.getWardId(), "roomNumber", r.getRoomNumber()),
                "Room created", HttpStatus.CREATED);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBedRequest {
        @NotNull
        private Long roomId;
        @NotBlank
        private String bedNumber;
        @NotNull
        private Bed.ChargeModel chargeModel;
        private BigDecimal tariffRate;
    }

    @PostMapping("/beds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BedView>> createBed(@Valid @RequestBody CreateBedRequest req) {
        Bed b = ipdService.createBed(req.getRoomId(), req.getBedNumber(), req.getChargeModel(), req.getTariffRate());
        return respond(BedView.from(b), "Bed created", HttpStatus.CREATED);
    }

    @GetMapping("/beds")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'NURSE', 'DOCTOR', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<BedView>>> bedBoard() {
        return respond(ipdService.getBedBoard().stream().map(BedView::from).toList(), "Bed board", HttpStatus.OK);
    }

    // ---------- admission lifecycle ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdmitRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private Long doctorId;
        @NotNull
        private Long bedId;
        private String diagnosis;
        private String notes;
    }

    @PostMapping("/admissions")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionView>> admit(@Valid @RequestBody AdmitRequest req) {
        IPDAdmission a = ipdService.admitPatient(req.getPatientId(), req.getDoctorId(), req.getBedId(),
                req.getDiagnosis(), req.getNotes());
        return respond(AdmissionView.from(a), "Patient admitted", HttpStatus.CREATED);
    }

    @GetMapping("/admissions/{id}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'NURSE', 'DOCTOR', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionView>> getAdmission(@PathVariable Long id) {
        return respond(AdmissionView.from(ipdService.getAdmission(id)), "Admission", HttpStatus.OK);
    }

    @GetMapping("/admissions/{id}/allocations")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'NURSE', 'DOCTOR', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AllocationView>>> allocations(@PathVariable Long id) {
        return respond(ipdService.getAllocations(id).stream().map(AllocationView::from).toList(),
                "Bed allocations", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRequest {
        @NotNull
        private Long newBedId;
    }

    @PostMapping("/admissions/{id}/transfer")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionView>> transfer(@PathVariable Long id,
                                                               @Valid @RequestBody TransferRequest req) {
        return respond(AdmissionView.from(ipdService.transferBed(id, req.getNewBedId())),
                "Bed transferred", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DischargeRequest {
        @NotNull
        private IPDAdmission.DischargeType dischargeType;
        /** Checklist item codes the discharging staff confirms (e.g. FINAL_BILL_CLEARED). */
        private List<String> acknowledgedChecklistItems;
        /** If the patient was infectious, set the precaution class — bed goes to ISOLATION, not VACANT. */
        private BedIsolation.IsolationType bedIsolationType;
        private String bedIsolationReason;
    }

    @PostMapping("/admissions/{id}/discharge")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionView>> discharge(@PathVariable Long id,
                                                                @Valid @RequestBody DischargeRequest req) {
        return respond(AdmissionView.from(
                        ipdService.discharge(id, req.getDischargeType(), req.getAcknowledgedChecklistItems(),
                                req.getBedIsolationType(), req.getBedIsolationReason())),
                "Patient discharged", HttpStatus.OK);
    }

    // ---------- bed isolation (infection control) ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IsolateBedRequest {
        @NotNull
        private BedIsolation.IsolationType isolationType;
        @NotBlank
        private String reason;
    }

    @PostMapping("/beds/{bedId}/isolate")
    @PreAuthorize("hasAnyRole('NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<IsolationView>> isolateBed(@PathVariable Long bedId,
                                                                 @Valid @RequestBody IsolateBedRequest req) {
        return respond(IsolationView.from(ipdService.isolateBed(bedId, req.getIsolationType(), req.getReason())),
                "Bed isolated", HttpStatus.CREATED);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClearIsolationRequest {
        @NotBlank
        private String clearanceNotes;
    }

    @PostMapping("/beds/{bedId}/clear-isolation")
    @PreAuthorize("hasAnyRole('NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<IsolationView>> clearIsolation(@PathVariable Long bedId,
                                                                     @Valid @RequestBody ClearIsolationRequest req) {
        return respond(IsolationView.from(ipdService.clearBedIsolation(bedId, req.getClearanceNotes())),
                "Bed isolation cleared", HttpStatus.OK);
    }

    @GetMapping("/beds/isolations")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<IsolationView>>> activeIsolations() {
        return respond(ipdService.getActiveIsolations().stream().map(IsolationView::from).toList(),
                "Active bed isolations", HttpStatus.OK);
    }

    // ---------- views ----------

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BedView {
        private Long id;
        private Long roomId;
        private String bedNumber;
        private Bed.ChargeModel chargeModel;
        private BigDecimal tariffRate;
        private Bed.BedStatus bedStatus;

        static BedView from(Bed b) {
            return BedView.builder()
                    .id(b.getId())
                    .roomId(b.getRoomId())
                    .bedNumber(b.getBedNumber())
                    .chargeModel(b.getChargeModel())
                    .tariffRate(b.getTariffRate())
                    .bedStatus(b.getBedStatus())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdmissionView {
        private Long id;
        private String admissionNumber;
        private Long patientId;
        private Long admittingDoctorId;
        private Long currentBedId;
        private IPDAdmission.AdmissionStatus admissionStatus;
        private LocalDateTime admittedAt;
        private LocalDateTime dischargedAt;
        private IPDAdmission.DischargeType dischargeType;
        private BigDecimal totalBedCharge;
        private String diagnosis;

        static AdmissionView from(IPDAdmission a) {
            return AdmissionView.builder()
                    .id(a.getId())
                    .admissionNumber(a.getAdmissionNumber())
                    .patientId(a.getPatientId())
                    .admittingDoctorId(a.getAdmittingDoctorId())
                    .currentBedId(a.getCurrentBedId())
                    .admissionStatus(a.getAdmissionStatus())
                    .admittedAt(a.getAdmittedAt())
                    .dischargedAt(a.getDischargedAt())
                    .dischargeType(a.getDischargeType())
                    .totalBedCharge(a.getTotalBedCharge())
                    .diagnosis(a.getDiagnosis())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AllocationView {
        private Long id;
        private Long bedId;
        private LocalDateTime allocatedAt;
        private LocalDateTime releasedAt;
        private Boolean isActive;
        private Bed.ChargeModel chargeModel;
        private BigDecimal tariffRate;
        private Integer unitsCharged;
        private BigDecimal allocationCharge;

        static AllocationView from(BedAllocation a) {
            return AllocationView.builder()
                    .id(a.getId())
                    .bedId(a.getBedId())
                    .allocatedAt(a.getAllocatedAt())
                    .releasedAt(a.getReleasedAt())
                    .isActive(a.getIsActive())
                    .chargeModel(a.getChargeModel())
                    .tariffRate(a.getTariffRate())
                    .unitsCharged(a.getUnitsCharged())
                    .allocationCharge(a.getAllocationCharge())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IsolationView {
        private Long id;
        private Long bedId;
        private Long sourceAdmissionId;
        private BedIsolation.IsolationType isolationType;
        private String reason;
        private LocalDateTime startedAt;
        private LocalDateTime expectedEndAt;
        private BedIsolation.IsolationStatus isolationStatus;
        private LocalDateTime clearedAt;
        private String clearanceNotes;

        static IsolationView from(BedIsolation i) {
            return IsolationView.builder()
                    .id(i.getId())
                    .bedId(i.getBedId())
                    .sourceAdmissionId(i.getSourceAdmissionId())
                    .isolationType(i.getIsolationType())
                    .reason(i.getReason())
                    .startedAt(i.getStartedAt())
                    .expectedEndAt(i.getExpectedEndAt())
                    .isolationStatus(i.getIsolationStatus())
                    .clearedAt(i.getClearedAt())
                    .clearanceNotes(i.getClearanceNotes())
                    .build();
        }
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
