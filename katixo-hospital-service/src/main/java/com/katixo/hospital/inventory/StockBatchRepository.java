package com.katixo.hospital.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    Optional<StockBatch> findByIdAndTenantId(Long id, String tenantId);

    Optional<StockBatch> findByTenantIdAndItemIdAndBatchNumber(String tenantId, Long itemId, String batchNumber);

    /** FEFO order: earliest expiry first (null expiry last), then oldest received. */
    @Query("SELECT b FROM StockBatch b WHERE b.tenantId = ?1 AND b.itemId = ?2 "
            + "AND b.quantityAvailable > 0 "
            + "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.id ASC")
    List<StockBatch> findAvailableFefo(String tenantId, Long itemId);

    /**
     * FEFO batches with a pessimistic write lock — used by the stock-issue path so
     * concurrent dispenses of the same item serialise and can't over-issue a batch
     * (the unlocked variant above is for read-only views).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM StockBatch b WHERE b.tenantId = ?1 AND b.itemId = ?2 "
            + "AND b.quantityAvailable > 0 "
            + "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.id ASC")
    List<StockBatch> findAvailableFefoForUpdate(String tenantId, Long itemId);

    @Query("SELECT COALESCE(SUM(b.quantityAvailable), 0) FROM StockBatch b "
            + "WHERE b.tenantId = ?1 AND b.itemId = ?2")
    BigDecimal totalAvailable(String tenantId, Long itemId);
}
