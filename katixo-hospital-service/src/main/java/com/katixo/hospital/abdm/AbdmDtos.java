package com.katixo.hospital.abdm;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class AbdmDtos {

    private AbdmDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkAbhaRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private String abhaNumber;
        private String abhaAddress;
        /** AADHAAR_OTP, MOBILE_OTP or DEMOGRAPHICS. Defaults to DEMOGRAPHICS when absent. */
        private AbhaLink.VerificationMethod verificationMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbhaLinkResponse {
        private Long id;
        private Long patientId;
        private String abhaNumber;        // formatted XX-XXXX-XXXX-XXXX for display
        private String abhaAddress;
        private String linkStatus;
        private String verificationMethod;
        private LocalDateTime linkedAt;

        public static AbhaLinkResponse from(AbhaLink link) {
            return AbhaLinkResponse.builder()
                    .id(link.getId())
                    .patientId(link.getPatientId())
                    .abhaNumber(AbhaNumberValidator.format(link.getAbhaNumber()))
                    .abhaAddress(link.getAbhaAddress())
                    .linkStatus(link.getLinkStatus().name())
                    .verificationMethod(link.getVerificationMethod().name())
                    .linkedAt(link.getLinkedAt())
                    .build();
        }
    }
}
