package com.katixo.hospital.common.repository;

import com.katixo.hospital.common.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity> extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {

    Optional<T> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);

    List<T> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);

    List<T> findByTenantIdAndBranchIdAndStatus(UUID tenantId, UUID branchId, BaseEntity.EntityStatus status);
}
