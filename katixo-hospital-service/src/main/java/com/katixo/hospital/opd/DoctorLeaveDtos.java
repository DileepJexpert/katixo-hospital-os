package com.katixo.hospital.opd;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DoctorLeaveDtos {

    private DoctorLeaveDtos() {
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull
        private LocalDate leaveStartDate;

        @NotNull
        private LocalDate leaveEndDate;

        @NotNull
        private DoctorLeave.LeaveType leaveType;

        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        private String notes;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectionRequest {
        @NotBlank
        private String rejectionReason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LeaveResponse {
        private Long id;
        private Long doctorId;
        private LocalDate leaveStartDate;
        private LocalDate leaveEndDate;
        private DoctorLeave.LeaveType leaveType;
        private String reason;
        private DoctorLeave.LeaveStatus status;
        private Long approvedBy;
        private LocalDateTime approvedAt;
        private String rejectionReason;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static LeaveResponse from(DoctorLeave leave) {
            return LeaveResponse.builder()
                    .id(leave.getId())
                    .doctorId(leave.getDoctorId())
                    .leaveStartDate(leave.getLeaveStartDate())
                    .leaveEndDate(leave.getLeaveEndDate())
                    .leaveType(leave.getLeaveType())
                    .reason(leave.getReason())
                    .status(leave.getStatus())
                    .approvedBy(leave.getApprovedBy())
                    .approvedAt(leave.getApprovedAt())
                    .rejectionReason(leave.getRejectionReason())
                    .createdAt(leave.getCreatedAt())
                    .updatedAt(leave.getUpdatedAt())
                    .build();
        }
    }
}
