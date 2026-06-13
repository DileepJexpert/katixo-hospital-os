package com.katixo.hospital.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    Optional<Item> findByTenantIdAndBranchIdAndCode(String tenantId, Long branchId, String code);

    @Query("SELECT i FROM Item i WHERE i.tenantId = ?1 AND i.branchId = ?2 "
            + "AND (LOWER(i.code) LIKE LOWER(CONCAT('%', ?3, '%')) "
            + "OR LOWER(i.name) LIKE LOWER(CONCAT('%', ?3, '%'))) ORDER BY i.name")
    List<Item> search(String tenantId, Long branchId, String term);

    List<Item> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);
}
