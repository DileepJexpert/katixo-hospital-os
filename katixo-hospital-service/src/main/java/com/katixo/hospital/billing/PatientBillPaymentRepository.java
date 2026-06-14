package com.katixo.hospital.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientBillPaymentRepository extends JpaRepository<PatientBillPayment, Long> {

    List<PatientBillPayment> findByTenantIdAndBillIdOrderById(String tenantId, Long billId);

    Optional<PatientBillPayment> findByIdAndTenantId(Long id, String tenantId);
}
