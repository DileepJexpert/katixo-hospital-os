package com.katixo.hospital.tpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpaCaseEventRepository extends JpaRepository<TpaCaseEvent, Long> {

    List<TpaCaseEvent> findByTenantIdAndTpaCaseIdOrderById(String tenantId, Long tpaCaseId);
}
