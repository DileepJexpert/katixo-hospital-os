package com.katixo.hospital.tpa;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface TPADocumentRepository extends BaseRepository<TPADocument> {
    List<TPADocument> findByTenantIdAndTpaCaseId(String tenantId, Long tpaCaseId);
    List<TPADocument> findByTenantIdAndTpaCaseIdAndRequiredTrue(String tenantId, Long tpaCaseId);
    List<TPADocument> findByTenantIdAndTpaCaseIdAndSubmittedFalse(String tenantId, Long tpaCaseId);
}
