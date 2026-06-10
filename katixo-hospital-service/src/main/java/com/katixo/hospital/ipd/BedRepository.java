package com.katixo.hospital.ipd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BedRepository extends BaseRepository<Bed> {

    @Query("SELECT b FROM Bed b WHERE b.tenantId = :tenantId AND b.branchId = :branchId " +
            "AND b.status = 'ACTIVE' ORDER BY b.roomId, b.bedNumber")
    List<Bed> findBedBoard(@Param("tenantId") String tenantId, @Param("branchId") Long branchId);
}
