package com.katixo.hospital.staff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CreateStaffRequest {
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public String role;
    public String department;
    public String specialization;
    public LocalDate dateOfJoining;
    public Boolean canApproveDiscount;
    public Boolean canApproveDischargeSummary;
    public Boolean canApproveLabReport;
    public String notes;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UpdateStaffRequest {
    public String firstName;
    public String lastName;
    public String phone;
    public String department;
    public String specialization;
    public Boolean canApproveDiscount;
    public Boolean canApproveDischargeSummary;
    public Boolean canApproveLabReport;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StaffResponse {
    public Long id;
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public String role;
    public String department;
    public String specialization;
    public LocalDate dateOfJoining;
    public Boolean isActive;
    public Boolean canApproveDiscount;
    public Boolean canApproveDischargeSummary;
    public Boolean canApproveLabReport;
    public String notes;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
