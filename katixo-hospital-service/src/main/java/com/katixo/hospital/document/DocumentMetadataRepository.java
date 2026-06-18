package com.katixo.hospital.document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    Optional<DocumentMetadata> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<DocumentMetadata> findByTenantIdAndBranchIdAndEntityTypeAndEntityIdOrderByIdDesc(
            String tenantId, Long branchId, String entityType, Long entityId);

    List<DocumentMetadata> findByTenantIdAndBranchIdOrderByIdDesc(
            String tenantId, Long branchId, Pageable pageable);
}
