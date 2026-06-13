package com.katixo.hospital.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByTenantIdAndBranchIdAndCode(String tenantId, Long branchId, String code);

    List<Account> findByTenantIdAndBranchIdOrderByCode(String tenantId, Long branchId);
}
