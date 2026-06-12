package com.katixo.hospital.opd;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class OPDDtos {

    private OPDDtos() {
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateWalkInRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private Long doctorId;
        private Long referralDoctorId;
        private String chiefComplaint;
        private Integer priority;
        private String priorityReason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookAppointmentRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private Long doctorId;
        @NotNull
        private LocalDate appointmentDate;
        @NotNull
        private LocalTime slotStart;
        @NotNull
        private LocalTime slotEnd;
        private String notes;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelAppointmentRequest {
        @NotNull
        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteConsultationRequest {
        private String diagnosis;
        private String advice;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VisitResponse {
        private Long id;
        private String visitNumber;
        private Long patientId;
        private Long primaryDoctorId;
        private Long referralDoctorId;
        private OPDVisit.VisitType visitType;
        private OPDVisit.VisitStatus visitStatus;
        private String chiefComplaint;
        private BigDecimal consultationFee;
        private OPDVisit.FeeType feeType;
        private Long parentVisitId;
        private String diagnosis;
        private String advice;
        private LocalDateTime consultationStartedAt;
        private LocalDateTime consultationEndedAt;
        private LocalDateTime createdAt;

        public static VisitResponse from(OPDVisit v) {
            return VisitResponse.builder()
                    .id(v.getId())
                    .visitNumber(v.getVisitNumber())
                    .patientId(v.getPatientId())
                    .primaryDoctorId(v.getPrimaryDoctorId())
                    .referralDoctorId(v.getReferralDoctorId())
                    .visitType(v.getVisitType())
                    .visitStatus(v.getVisitStatus())
                    .chiefComplaint(v.getChiefComplaint())
                    .consultationFee(v.getConsultationFee())
                    .feeType(v.getFeeType())
                    .parentVisitId(v.getParentVisitId())
                    .diagnosis(v.getDiagnosis())
                    .advice(v.getAdvice())
                    .consultationStartedAt(v.getConsultationStartedAt())
                    .consultationEndedAt(v.getConsultationEndedAt())
                    .createdAt(v.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenResponse {
        private Long id;
        private Long visitId;
        private Long doctorId;
        private Integer tokenNumber;
        private LocalDate tokenDate;
        private Integer priority;
        private QueueToken.QueueStatus queueStatus;
        private LocalDateTime calledAt;

        public static TokenResponse from(QueueToken t) {
            return TokenResponse.builder()
                    .id(t.getId())
                    .visitId(t.getVisitId())
                    .doctorId(t.getDoctorId())
                    .tokenNumber(t.getTokenNumber())
                    .tokenDate(t.getTokenDate())
                    .priority(t.getPriority())
                    .queueStatus(t.getQueueStatus())
                    .calledAt(t.getCalledAt())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppointmentResponse {
        private Long id;
        private Long patientId;
        private Long doctorId;
        private LocalDate appointmentDate;
        private LocalTime slotStart;
        private LocalTime slotEnd;
        private Appointment.AppointmentStatus appointmentStatus;
        private Long visitId;
        private String notes;

        public static AppointmentResponse from(Appointment a) {
            return AppointmentResponse.builder()
                    .id(a.getId())
                    .patientId(a.getPatientId())
                    .doctorId(a.getDoctorId())
                    .appointmentDate(a.getAppointmentDate())
                    .slotStart(a.getSlotStart())
                    .slotEnd(a.getSlotEnd())
                    .appointmentStatus(a.getAppointmentStatus())
                    .visitId(a.getVisitId())
                    .notes(a.getNotes())
                    .build();
        }
    }
}
