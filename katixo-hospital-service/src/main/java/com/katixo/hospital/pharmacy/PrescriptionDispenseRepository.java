package com.katixo.hospital.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PrescriptionDispenseRepository extends JpaRepository<PrescriptionDispense, Long> {

    Optional<PrescriptionDispense> findByTenantIdAndBranchIdAndPrescriptionId(String tenantId, Long branchId, Long prescriptionId);

    @Query("SELECT pd FROM PrescriptionDispense pd WHERE pd.tenantId = ?1 AND pd.branchId = ?2 " +
           "AND pd.dispenseStatus = ?3 ORDER BY pd.createdAt ASC")
    List<PrescriptionDispense> findByTenantIdAndBranchIdAndStatus(String tenantId, Long branchId, PrescriptionDispense.DispenseStatus status);

    @Query("SELECT pd FROM PrescriptionDispense pd WHERE pd.tenantId = ?1 AND pd.branchId = ?2 " +
           "AND pd.patientId = ?3 ORDER BY pd.createdAt DESC")
    List<PrescriptionDispense> findByPatientId(String tenantId, Long branchId, Long patientId);

    /** Fully-dispensed prescriptions for a visit that produced a pharmacy sale. */
    List<PrescriptionDispense> findByTenantIdAndVisitIdAndSaleIdNotNull(String tenantId, Long visitId);
}
