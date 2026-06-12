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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCareContextRequest {
        @NotNull
        private CareContext.SourceType sourceType;
        @NotNull
        private Long sourceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareContextResponse {
        private Long id;
        private Long patientId;
        private String careContextReference;
        private String displayName;
        private String sourceType;
        private Long sourceId;
        private String linkStatus;

        public static CareContextResponse from(CareContext ctx) {
            return CareContextResponse.builder()
                    .id(ctx.getId())
                    .patientId(ctx.getPatientId())
                    .careContextReference(ctx.getCareContextReference())
                    .displayName(ctx.getDisplayName())
                    .sourceType(ctx.getSourceType().name())
                    .sourceId(ctx.getSourceId())
                    .linkStatus(ctx.getLinkStatus().name())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordConsentRequest {
        @NotNull
        private Long patientId;
        /** ABDM purpose-of-use code; defaults to CAREMGT when absent. */
        private String purposeCode;
        @NotNull
        private java.util.List<String> hiTypes;
        @NotNull
        private LocalDateTime dataFrom;
        @NotNull
        private LocalDateTime dataTo;
        @NotNull
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentResponse {
        private Long id;
        private String artifactId;
        private Long patientId;
        private String purposeCode;
        private String hiTypes;
        private LocalDateTime dataFrom;
        private LocalDateTime dataTo;
        private LocalDateTime expiresAt;
        private String consentStatus;
        private LocalDateTime grantedAt;

        public static ConsentResponse from(ConsentArtifact artifact) {
            return ConsentResponse.builder()
                    .id(artifact.getId())
                    .artifactId(artifact.getArtifactId())
                    .patientId(artifact.getPatientId())
                    .purposeCode(artifact.getPurposeCode())
                    .hiTypes(artifact.getHiTypes())
                    .dataFrom(artifact.getDataFrom())
                    .dataTo(artifact.getDataTo())
                    .expiresAt(artifact.getExpiresAt())
                    .consentStatus(artifact.getConsentStatus().name())
                    .grantedAt(artifact.getGrantedAt())
                    .build();
        }
    }
}
