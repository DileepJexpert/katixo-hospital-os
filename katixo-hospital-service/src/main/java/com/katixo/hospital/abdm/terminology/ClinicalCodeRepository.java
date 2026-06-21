package com.katixo.hospital.abdm.terminology;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalCodeRepository extends BaseRepository<ClinicalCode> {

    Optional<ClinicalCode> findByTenantIdAndCategoryAndLocalTerm(
            String tenantId, String category, String localTerm);

    @Query("SELECT c FROM ClinicalCode c WHERE c.tenantId = :tenantId AND c.category = :category " +
            "AND (:q IS NULL OR LOWER(c.localTerm) LIKE :q OR LOWER(c.display) LIKE :q OR c.code LIKE :q) " +
            "ORDER BY c.localTerm ASC")
    List<ClinicalCode> search(@Param("tenantId") String tenantId,
                              @Param("category") String category,
                              @Param("q") String q,
                              org.springframework.data.domain.Pageable pageable);
}
