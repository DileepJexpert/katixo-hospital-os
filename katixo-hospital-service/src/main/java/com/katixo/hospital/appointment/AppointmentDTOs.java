package com.katixo.hospital.appointment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class BookAppointmentRequest {
    public Long patientId;
    public Long doctorId;
    public LocalDateTime appointmentDateTime;
    public String reason;
    public String appointmentType;
    public Boolean isOnline;
    public String notes;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CancelAppointmentRequest {
    public String reason;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponse {
    public Long id;
    public Long patientId;
    public Long doctorId;
    public LocalDateTime appointmentDateTime;
    public LocalDateTime appointmentEndTime;
    public String reason;
    public String appointmentType;
    public String appointmentStatus;
    public Long confirmedBy;
    public LocalDateTime confirmedAt;
    public Long cancelledBy;
    public LocalDateTime cancelledAt;
    public String cancellationReason;
    public Long completedBy;
    public LocalDateTime completedAt;
    public String notes;
    public String appointmentLink;
    public Boolean isOnline;
    public Long relatedVisitId;
    public LocalDateTime createdAt;
}
