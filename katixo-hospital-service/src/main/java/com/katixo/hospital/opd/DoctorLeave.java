package com.katixo.hospital.opd;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_leave", indexes = {
        @Index(name = "idx_doctor_leave_doctor_date", columnList = "tenant_id,branch_id,doctor_id,leave_start_date,leave_end_date"),
        @Index(name = "idx_doctor_leave_status", columnList = "status"),
        @Index(name = "idx_doctor_leave_date_range", columnList = "leave_start_date,leave_end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @Column(nullable = false, updatable = false)
    private Long doctorId;

    @Column(nullable = false, updatable = false)
    private LocalDate leaveStartDate;

    @Column(nullable = false, updatable = false)
    private LocalDate leaveEndDate;

    @Column(nullable = false, length = 50, updatable = false)
    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column
    private Long approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Long updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum LeaveType {
        CASUAL, SICK, EARNED, UNPAID, CONFERENCE, SABBATICAL
    }

    public enum LeaveStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    public boolean isActive(LocalDate checkDate) {
        return status == LeaveStatus.APPROVED &&
                !checkDate.isBefore(leaveStartDate) &&
                !checkDate.isAfter(leaveEndDate);
    }
}
