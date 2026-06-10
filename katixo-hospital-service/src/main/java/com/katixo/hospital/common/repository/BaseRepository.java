package com.katixo.hospital.common.repository;

import com.katixo.hospital.common.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    Optional<T> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<T> findByTenantIdAndBranchId(String tenantId, Long branchId);

    List<T> findByTenantIdAndBranchIdAndStatus(String tenantId, Long branchId, BaseEntity.EntityStatus status);
}
