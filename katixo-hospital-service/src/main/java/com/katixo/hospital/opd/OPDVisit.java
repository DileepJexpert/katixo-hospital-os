package com.katixo.hospital.opd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "opd_visit", indexes = {
        @Index(name = "idx_opd_visit_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_opd_visit_patient", columnList = "patient_id,created_at"),
        @Index(name = "idx_opd_visit_doctor", columnList = "primary_doctor_id,created_at"),
        @Index(name = "idx_opd_visit_status", columnList = "visit_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OPDVisit extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String visitNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long primaryDoctorId;

    @Column
    private Long referralDoctorId;

    @Column
    private Long departmentId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VisitType visitType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VisitStatus visitStatus = VisitStatus.REGISTERED;

    @Column(columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FeeType feeType = FeeType.FULL;

    @Column
    private Long parentVisitId;

    @Column
    private LocalDateTime consultationStartedAt;

    @Column
    private LocalDateTime consultationEndedAt;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String advice;

    public enum VisitType {
        WALK_IN, APPOINTMENT, FOLLOW_UP
    }

    public enum VisitStatus {
        REGISTERED, IN_QUEUE, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW
    }

    public enum FeeType {
        FULL, REDUCED, FREE
    }
}
