package com.katixo.hospital.patient;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PatientCreditDtos {

    private PatientCreditDtos() {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreditAccountResponse {
        private Long id;
        private Long patientId;
        private BigDecimal availableBalance;
        private BigDecimal totalDebited;
        private BigDecimal totalCredited;
        private BigDecimal creditLimit;
        private PatientCreditAccount.CreditStatus status;
        private LocalDateTime lastTransactionAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static CreditAccountResponse from(PatientCreditAccount account) {
            return CreditAccountResponse.builder()
                    .id(account.getId())
                    .patientId(account.getPatient().getId())
                    .availableBalance(account.getAvailableBalance())
                    .totalDebited(account.getTotalDebited())
                    .totalCredited(account.getTotalCredited())
                    .creditLimit(account.getCreditLimit())
                    .status(account.getStatus())
                    .lastTransactionAt(account.getLastTransactionAt())
                    .createdAt(account.getCreatedAt())
                    .updatedAt(account.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionResponse {
        private Long id;
        private PatientCreditTransaction.TransactionType transactionType;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private String sourceType;
        private String sourceRef;
        private String description;
        private LocalDateTime createdAt;

        public static TransactionResponse from(PatientCreditTransaction transaction) {
            return TransactionResponse.builder()
                    .id(transaction.getId())
                    .transactionType(transaction.getTransactionType())
                    .amount(transaction.getAmount())
                    .balanceAfter(transaction.getBalanceAfter())
                    .sourceType(transaction.getSourceType())
                    .sourceRef(transaction.getSourceRef())
                    .description(transaction.getDescription())
                    .createdAt(transaction.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustmentRequest {
        @DecimalMin(value = "-99999.99")
        private BigDecimal amount;

        @NotBlank
        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetLimitRequest {
        @DecimalMin(value = "0.00")
        private BigDecimal creditLimit;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        private PatientCreditAccount.CreditStatus status;
    }
}
