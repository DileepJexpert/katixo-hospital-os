package com.katixo.hospital.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<JournalEntry> findByTenantIdAndBranchIdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            String tenantId, Long branchId, LocalDate from, LocalDate to);

    @Query(value = "SELECT nextval('journal_entry_seq')", nativeQuery = true)
    long nextEntrySequence();
}
