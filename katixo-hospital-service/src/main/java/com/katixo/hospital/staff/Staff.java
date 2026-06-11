package com.katixo.hospital.staff;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "staff", indexes = {
        @Index(name = "idx_staff_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_staff_role", columnList = "role"),
        @Index(name = "idx_staff_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Staff extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private StaffRole role;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(length = 100)
    private String specialization;

    @Column
    private LocalDate dateOfJoining;

    @Column
    private LocalDate dateOfLeaving;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column
    private Boolean canApproveDiscount = false;

    @Column
    private Boolean canApproveDischargeSummary = false;

    @Column
    private Boolean canApproveLabReport = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum StaffRole {
        DOCTOR,
        NURSE,
        NURSE_SUPERVISOR,
        LAB_TECHNICIAN,
        RADIOLOGIST,
        PHARMACIST,
        FRONT_DESK,
        ADMIN,
        OWNER
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
