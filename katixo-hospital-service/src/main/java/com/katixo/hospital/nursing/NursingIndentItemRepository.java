package com.katixo.hospital.nursing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NursingIndentItemRepository extends JpaRepository<NursingIndentItem, Long> {

    List<NursingIndentItem> findByTenantIdAndIndentIdOrderById(String tenantId, Long indentId);
}
