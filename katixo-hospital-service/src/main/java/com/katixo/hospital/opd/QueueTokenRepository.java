package com.katixo.hospital.opd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueTokenRepository extends BaseRepository<QueueToken> {

    @Query("SELECT COALESCE(MAX(t.tokenNumber), 0) FROM QueueToken t WHERE t.tenantId = :tenantId " +
            "AND t.branchId = :branchId AND t.doctorId = :doctorId AND t.tokenDate = :tokenDate")
    Integer findMaxTokenNumber(@Param("tenantId") String tenantId,
                               @Param("branchId") Long branchId,
                               @Param("doctorId") Long doctorId,
                               @Param("tokenDate") LocalDate tokenDate);

    @Query("SELECT t FROM QueueToken t WHERE t.tenantId = :tenantId AND t.branchId = :branchId " +
            "AND t.doctorId = :doctorId AND t.tokenDate = :tokenDate " +
            "AND t.queueStatus IN :statuses " +
            "ORDER BY t.priority DESC, t.tokenNumber ASC")
    List<QueueToken> findWorklist(@Param("tenantId") String tenantId,
                                  @Param("branchId") Long branchId,
                                  @Param("doctorId") Long doctorId,
                                  @Param("tokenDate") LocalDate tokenDate,
                                  @Param("statuses") List<QueueToken.QueueStatus> statuses);

    @Query("SELECT t FROM QueueToken t WHERE t.tenantId = :tenantId AND t.branchId = :branchId " +
            "AND t.doctorId = :doctorId AND t.tokenDate = :tokenDate AND t.queueStatus = 'WAITING' " +
            "ORDER BY t.priority DESC, t.tokenNumber ASC LIMIT 1")
    Optional<QueueToken> findNextWaiting(@Param("tenantId") String tenantId,
                                         @Param("branchId") Long branchId,
                                         @Param("doctorId") Long doctorId,
                                         @Param("tokenDate") LocalDate tokenDate);

    Optional<QueueToken> findByTenantIdAndVisitId(String tenantId, Long visitId);
}
