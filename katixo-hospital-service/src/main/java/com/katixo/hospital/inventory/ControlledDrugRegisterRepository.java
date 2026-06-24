package com.katixo.hospital.inventory;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ControlledDrugRegisterRepository extends BaseRepository<ControlledDrugRegisterEntry> {

    @Query("SELECT e FROM ControlledDrugRegisterEntry e WHERE e.tenantId = :tenantId AND e.branchId = :branchId " +
            "AND (:from IS NULL OR e.entryDate >= :from) AND (:to IS NULL OR e.entryDate <= :to) " +
            "AND (:schedule IS NULL OR e.drugSchedule = :schedule) " +
            "ORDER BY e.entryDate DESC, e.id DESC")
    List<ControlledDrugRegisterEntry> search(@Param("tenantId") String tenantId,
                                             @Param("branchId") Long branchId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("schedule") Item.DrugSchedule schedule);
}
