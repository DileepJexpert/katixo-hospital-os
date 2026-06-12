package com.katixo.hospital.staff;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StaffService {

    private final StaffRepository staffRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    public StaffResponse createStaff(CreateStaffRequest request) {
        var ctx = TenantContext.get();

        staffRepository.findByTenantIdAndBranchIdAndEmail(ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), request.email)
                .ifPresent(s -> {
                    throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email already registered");
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
        staff.setCreatedBy(Long.parseLong(ctx.getUserId()));
        staff.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        staff.setStatus(BaseEntity.EntityStatus.ACTIVE);

        staff = staffRepository.save(staff);

        auditService.audit("Staff", String.valueOf(staff.getId()), AuditLog.AuditAction.CREATE,
                null,
                Map.of("name", staff.getFullName(), "email", staff.getEmail(), "role", staff.getRole().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("Staff", String.valueOf(staff.getId()), "staff.created",
                Map.of("staffId", staff.getId(), "role", staff.getRole().name()));

        return toResponse(staff);
    }

    public List<StaffResponse> listActiveStaff() {
        var ctx = TenantContext.get();
        var staff = staffRepository.findByTenantIdAndBranchIdAndIsActive(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                true
        );
        return staff.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<StaffResponse> listStaffByRole(String role) {
        var ctx = TenantContext.get();
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
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Staff member not found"));
        return toResponse(staff);
    }

    public StaffResponse updateStaff(Long staffId, UpdateStaffRequest request) {
        var ctx = TenantContext.get();
        var staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Staff member not found"));

        if (request.firstName != null) staff.setFirstName(request.firstName);
        if (request.lastName != null) staff.setLastName(request.lastName);
        if (request.phone != null) staff.setPhone(request.phone);
        if (request.department != null) staff.setDepartment(request.department);
        if (request.specialization != null) staff.setSpecialization(request.specialization);
        if (request.canApproveDiscount != null) staff.setCanApproveDiscount(request.canApproveDiscount);
        if (request.canApproveDischargeSummary != null) staff.setCanApproveDischargeSummary(request.canApproveDischargeSummary);
        if (request.canApproveLabReport != null) staff.setCanApproveLabReport(request.canApproveLabReport);

        staff.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        staff = staffRepository.save(staff);

        auditService.audit("Staff", String.valueOf(staff.getId()), AuditLog.AuditAction.UPDATE,
                null,
                Map.of("name", staff.getFullName()),
                UUID.randomUUID().toString());

        outboxEventService.publish("Staff", String.valueOf(staff.getId()), "staff.updated",
                Map.of("staffId", staff.getId()));

        return toResponse(staff);
    }

    public void deactivateStaff(Long staffId) {
        var ctx = TenantContext.get();
        var staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Staff member not found"));

        staff.setIsActive(false);
        staff.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        staffRepository.save(staff);

        auditService.audit("Staff", String.valueOf(staff.getId()), AuditLog.AuditAction.DELETE,
                Map.of("isActive", true),
                Map.of("isActive", false),
                UUID.randomUUID().toString());

        outboxEventService.publish("Staff", String.valueOf(staff.getId()), "staff.deactivated",
                Map.of("staffId", staff.getId()));
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
