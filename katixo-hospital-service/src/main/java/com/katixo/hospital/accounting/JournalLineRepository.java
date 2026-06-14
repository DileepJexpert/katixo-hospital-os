package com.katixo.hospital.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByTenantIdAndJournalEntryIdOrderById(String tenantId, Long journalEntryId);
}
