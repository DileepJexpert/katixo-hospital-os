package com.katixo.hospital.staff;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.ApiException;
import com.katixo.hospital.outbox.OutboxEvent;
import com.katixo.hospital.outbox.OutboxPublisher;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StaffService {

    private final StaffRepository staffRepository;
    private final AuditService auditService;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    public StaffResponse createStaff(CreateStaffRequest request) {
        var ctx = tenantContext.current();

        staffRepository.findByTenantIdAndBranchIdAndEmail(ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), request.email)
                .ifPresent(s -> {
                    throw new ApiException("EMAIL_ALREADY_EXISTS", "Email already registered");
                });

        var staff = new Staff();
        staff.setTenantId(ctx.getTenantId());
        staff.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        staff.setBranchId(Long.parseLong(ctx.getBranchId()));
        staff.setFirstName(request.firstName);
        staff.setLastName(request.lastName);
        staff.setEmail(request.email);
        staff.setPhone(request.phone);
        staff.setRole(Staff.StaffRole.valueOf(request.role));
        staff.setDepartment(request.department);
        staff.setSpecialization(request.specialization);
        staff.setDateOfJoining(request.dateOfJoining);
        staff.setIsActive(true);
        staff.setCanApproveDiscount(request.canApproveDiscount);
        staff.setCanApproveDischargeSummary(request.canApproveDischargeSummary);
        staff.setCanApproveLabReport(request.canApproveLabReport);
        staff.setNotes(request.notes);
        staff.setCreatedBy(ctx.getCurrentUserId());
        staff.setUpdatedBy(ctx.getCurrentUserId());

        staff = staffRepository.save(staff);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("CREATE_STAFF")
                .entityType("Staff")
                .entityId(staff.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "staff.created",
                "Staff",
                staff.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(staff);
    }

    public List<StaffResponse> listActiveStaff() {
        var ctx = tenantContext.current();
        var staff = staffRepository.findByTenantIdAndBranchIdAndIsActive(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                true
        );
        return staff.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<StaffResponse> listStaffByRole(String role) {
        var ctx = tenantContext.current();
        var staffRole = Staff.StaffRole.valueOf(role);
        var staff = staffRepository.findByTenantIdAndBranchIdAndRole(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                staffRole
        );
        return staff.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public StaffResponse getStaffById(Long staffId) {
        var staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ApiException("STAFF_NOT_FOUND", "Staff member not found"));
        return toResponse(staff);
    }

    public StaffResponse updateStaff(Long staffId, UpdateStaffRequest request) {
        var ctx = tenantContext.current();
        var staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ApiException("STAFF_NOT_FOUND", "Staff member not found"));

        if (request.firstName != null) staff.setFirstName(request.firstName);
        if (request.lastName != null) staff.setLastName(request.lastName);
        if (request.phone != null) staff.setPhone(request.phone);
        if (request.department != null) staff.setDepartment(request.department);
        if (request.specialization != null) staff.setSpecialization(request.specialization);
        if (request.canApproveDiscount != null) staff.setCanApproveDiscount(request.canApproveDiscount);
        if (request.canApproveDischargeSummary != null) staff.setCanApproveDischargeSummary(request.canApproveDischargeSummary);
        if (request.canApproveLabReport != null) staff.setCanApproveLabReport(request.canApproveLabReport);

        staff.setUpdatedBy(ctx.getCurrentUserId());
        staff = staffRepository.save(staff);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("UPDATE_STAFF")
                .entityType("Staff")
                .entityId(staff.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "staff.updated",
                "Staff",
                staff.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(staff);
    }

    public void deactivateStaff(Long staffId) {
        var ctx = tenantContext.current();
        var staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ApiException("STAFF_NOT_FOUND", "Staff member not found"));

        staff.setIsActive(false);
        staff.setUpdatedBy(ctx.getCurrentUserId());
        staffRepository.save(staff);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("DEACTIVATE_STAFF")
                .entityType("Staff")
                .entityId(staff.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "staff.deactivated",
                "Staff",
                staff.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));
    }

    private StaffResponse toResponse(Staff staff) {
        return StaffResponse.builder()
                .id(staff.getId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .email(staff.getEmail())
                .phone(staff.getPhone())
                .role(staff.getRole().toString())
                .department(staff.getDepartment())
                .specialization(staff.getSpecialization())
                .dateOfJoining(staff.getDateOfJoining())
                .isActive(staff.getIsActive())
                .canApproveDiscount(staff.getCanApproveDiscount())
                .canApproveDischargeSummary(staff.getCanApproveDischargeSummary())
                .canApproveLabReport(staff.getCanApproveLabReport())
                .notes(staff.getNotes())
                .build();
    }
}
