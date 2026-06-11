package com.katixo.hospital.tpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TPADocumentRepository extends JpaRepository<TPADocument, Long> {
    List<TPADocument> findByTpaCaseId(Long tpaCaseId);
    List<TPADocument> findByTpaCaseIdAndRequiredTrue(Long tpaCaseId);
    List<TPADocument> findByTpaCaseIdAndSubmittedFalse(Long tpaCaseId);
}
