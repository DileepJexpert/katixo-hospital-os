package com.katixo.hospital.opd;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {

    @Query("SELECT l FROM DoctorLeave l WHERE l.tenantId = :tenantId AND l.branchId = :branchId " +
            "AND l.doctorId = :doctorId AND l.status = 'APPROVED' " +
            "AND :checkDate >= l.leaveStartDate AND :checkDate <= l.leaveEndDate")
    List<DoctorLeave> findActiveLeaveOnDate(@Param("tenantId") String tenantId,
                                             @Param("branchId") Long branchId,
                                             @Param("doctorId") Long doctorId,
                                             @Param("checkDate") LocalDate checkDate);

    @Query("SELECT l FROM DoctorLeave l WHERE l.tenantId = :tenantId AND l.branchId = :branchId " +
            "AND l.doctorId = :doctorId AND l.status = 'APPROVED' " +
            "AND NOT (l.leaveEndDate < :startDate OR l.leaveStartDate > :endDate)")
    List<DoctorLeave> findActiveLeaveInRange(@Param("tenantId") String tenantId,
                                              @Param("branchId") Long branchId,
                                              @Param("doctorId") Long doctorId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    Page<DoctorLeave> findByTenantIdAndBranchIdAndDoctorIdOrderByLeaveStartDateDesc(
            String tenantId, Long branchId, Long doctorId, Pageable pageable);

    Page<DoctorLeave> findByTenantIdAndBranchIdAndStatusOrderByLeaveStartDateDesc(
            String tenantId, Long branchId, DoctorLeave.LeaveStatus status, Pageable pageable);
}
