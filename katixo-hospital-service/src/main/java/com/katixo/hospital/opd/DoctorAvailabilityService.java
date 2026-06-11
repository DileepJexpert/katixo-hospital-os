package com.katixo.hospital.opd;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DoctorAvailabilityService {

    private final DoctorLeaveRepository leaveRepository;

    @Transactional(readOnly = true)
    public boolean isAvailable(Long doctorId, LocalDate date) {
        var context = TenantContext.get();
        List<DoctorLeave> activeLeaves = leaveRepository.findActiveLeaveOnDate(
                context.getTenantId(),
                Long.parseLong(context.getBranchId()),
                doctorId,
                date);
        return activeLeaves.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<DoctorLeave> getActiveLeaveForPeriod(Long doctorId, LocalDate startDate, LocalDate endDate) {
        var context = TenantContext.get();
        return leaveRepository.findActiveLeaveInRange(
                context.getTenantId(),
                Long.parseLong(context.getBranchId()),
                doctorId,
                startDate,
                endDate);
    }

    public DoctorLeave createLeave(Long doctorId, LocalDate startDate, LocalDate endDate,
                                    DoctorLeave.LeaveType leaveType, String reason) {
        var context = TenantContext.get();

        if (!endDate.isAfter(startDate)) {
            throw new BusinessException("INVALID_LEAVE_PERIOD",
                    "Leave end date must be after start date");
        }

        DoctorLeave leave = new DoctorLeave();
        leave.setTenantId(context.getTenantId());
        leave.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        leave.setBranchId(Long.parseLong(context.getBranchId()));
        leave.setDoctorId(doctorId);
        leave.setLeaveStartDate(startDate);
        leave.setLeaveEndDate(endDate);
        leave.setLeaveType(leaveType);
        leave.setReason(reason);
        leave.setStatus(DoctorLeave.LeaveStatus.PENDING);
        leave.setCreatedBy(Long.parseLong(context.getUserId()));
        leave.setCreatedAt(LocalDateTime.now());
        leave.setUpdatedBy(Long.parseLong(context.getUserId()));
        leave.setUpdatedAt(LocalDateTime.now());

        DoctorLeave saved = leaveRepository.save(leave);
        log.info("Leave created for doctor {} from {} to {} ({})",
                doctorId, startDate, endDate, leaveType);
        return saved;
    }

    public DoctorLeave approveLeave(Long leaveId, Long approvedBy) {
        var leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new BusinessException("LEAVE_NOT_FOUND",
                        "Leave record not found: " + leaveId));

        if (leave.getStatus() != DoctorLeave.LeaveStatus.PENDING) {
            throw new BusinessException("INVALID_STATE",
                    "Can only approve PENDING leaves, current status: " + leave.getStatus());
        }

        leave.setStatus(DoctorLeave.LeaveStatus.APPROVED);
        leave.setApprovedBy(approvedBy);
        leave.setApprovedAt(LocalDateTime.now());
        leave.setUpdatedBy(approvedBy);
        leave.setUpdatedAt(LocalDateTime.now());

        DoctorLeave saved = leaveRepository.save(leave);
        log.info("Leave {} approved for doctor {}", leaveId, leave.getDoctorId());
        return saved;
    }

    public DoctorLeave rejectLeave(Long leaveId, String rejectionReason, Long rejectedBy) {
        var leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new BusinessException("LEAVE_NOT_FOUND",
                        "Leave record not found: " + leaveId));

        if (leave.getStatus() != DoctorLeave.LeaveStatus.PENDING) {
            throw new BusinessException("INVALID_STATE",
                    "Can only reject PENDING leaves, current status: " + leave.getStatus());
        }

        leave.setStatus(DoctorLeave.LeaveStatus.REJECTED);
        leave.setRejectionReason(rejectionReason);
        leave.setApprovedBy(rejectedBy);
        leave.setApprovedAt(LocalDateTime.now());
        leave.setUpdatedBy(rejectedBy);
        leave.setUpdatedAt(LocalDateTime.now());

        DoctorLeave saved = leaveRepository.save(leave);
        log.info("Leave {} rejected for doctor {}", leaveId, leave.getDoctorId());
        return saved;
    }

    public DoctorLeave cancelLeave(Long leaveId) {
        var context = TenantContext.get();
        var leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new BusinessException("LEAVE_NOT_FOUND",
                        "Leave record not found: " + leaveId));

        if (leave.getStatus() == DoctorLeave.LeaveStatus.APPROVED &&
                LocalDate.now().isAfter(leave.getLeaveStartDate())) {
            throw new BusinessException("CANNOT_CANCEL_PAST_LEAVE",
                    "Cannot cancel leave that has already started");
        }

        leave.setStatus(DoctorLeave.LeaveStatus.CANCELLED);
        leave.setUpdatedBy(Long.parseLong(context.getUserId()));
        leave.setUpdatedAt(LocalDateTime.now());

        DoctorLeave saved = leaveRepository.save(leave);
        log.info("Leave {} cancelled for doctor {}", leaveId, leave.getDoctorId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<DoctorLeave> getLeaveRecords(Long doctorId, Pageable pageable) {
        var context = TenantContext.get();
        return leaveRepository.findByTenantIdAndBranchIdAndDoctorIdOrderByLeaveStartDateDesc(
                context.getTenantId(),
                Long.parseLong(context.getBranchId()),
                doctorId,
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<DoctorLeave> getPendingLeaves(Pageable pageable) {
        var context = TenantContext.get();
        return leaveRepository.findByTenantIdAndBranchIdAndStatusOrderByLeaveStartDateDesc(
                context.getTenantId(),
                Long.parseLong(context.getBranchId()),
                DoctorLeave.LeaveStatus.PENDING,
                pageable);
    }
}
