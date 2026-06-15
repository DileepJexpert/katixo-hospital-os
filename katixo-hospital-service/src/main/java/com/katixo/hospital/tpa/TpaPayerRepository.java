package com.katixo.hospital.tpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TpaPayerRepository extends JpaRepository<TpaPayer, Long> {

    Optional<TpaPayer> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<TpaPayer> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);

    @Query(value = "SELECT nextval('tpa_payer_seq')", nativeQuery = true)
    long nextPayerSequence();
}
