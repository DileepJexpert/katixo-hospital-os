package com.katixo.hospital.prescription;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class PrescriptionDtos {

    private PrescriptionDtos() {
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        @NotBlank
        private String medicineCode;
        @NotBlank
        private String medicineName;
        private String dosage;
        private String frequency;
        private Integer durationDays;
        private Integer quantity;
        private String instructions;

        public PrescriptionItem toEntity() {
            PrescriptionItem item = new PrescriptionItem();
            item.setMedicineCode(medicineCode);
            item.setMedicineName(medicineName);
            item.setDosage(dosage);
            item.setFrequency(frequency);
            item.setDurationDays(durationDays);
            item.setQuantity(quantity == null ? 1 : quantity);
            item.setInstructions(instructions);
            return item;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull
        private Long visitId;
        private String notes;
        @NotEmpty
        @Valid
        private List<ItemRequest> items;
        /** Set true to proceed despite an allergy conflict; requires allergyOverrideReason. */
        private boolean overrideAllergy;
        private String allergyOverrideReason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String notes;
        @NotEmpty
        @Valid
        private List<ItemRequest> items;
        /** Set true to proceed despite an allergy conflict; requires allergyOverrideReason. */
        private boolean overrideAllergy;
        private String allergyOverrideReason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemResponse {
        private Long id;
        private String medicineCode;
        private String medicineName;
        private String dosage;
        private String frequency;
        private Integer durationDays;
        private Integer quantity;
        private String instructions;

        public static ItemResponse from(PrescriptionItem i) {
            return ItemResponse.builder()
                    .id(i.getId())
                    .medicineCode(i.getMedicineCode())
                    .medicineName(i.getMedicineName())
                    .dosage(i.getDosage())
                    .frequency(i.getFrequency())
                    .durationDays(i.getDurationDays())
                    .quantity(i.getQuantity())
                    .instructions(i.getInstructions())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PrescriptionResponse {
        private Long id;
        private String prescriptionNumber;
        private Long visitId;
        private Long patientId;
        private Long doctorId;
        private Integer version;
        private Long parentPrescriptionId;
        private Boolean isLatest;
        private Prescription.PrescriptionStatus prescriptionStatus;
        private String notes;
        private LocalDateTime dispensedAt;
        private LocalDateTime createdAt;
        private List<ItemResponse> items;

        public static PrescriptionResponse from(Prescription p) {
            return PrescriptionResponse.builder()
                    .id(p.getId())
                    .prescriptionNumber(p.getPrescriptionNumber())
                    .visitId(p.getVisitId())
                    .patientId(p.getPatientId())
                    .doctorId(p.getDoctorId())
                    .version(p.getVersion())
                    .parentPrescriptionId(p.getParentPrescriptionId())
                    .isLatest(p.getIsLatest())
                    .prescriptionStatus(p.getPrescriptionStatus())
                    .notes(p.getNotes())
                    .dispensedAt(p.getDispensedAt())
                    .createdAt(p.getCreatedAt())
                    .items(p.getItems().stream().map(ItemResponse::from).toList())
                    .build();
        }
    }
}
