package com.katixo.hospital.ipd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends BaseRepository<Room> {
    List<Room> findByTenantIdAndBranchIdOrderByRoomNumber(String tenantId, Long branchId);
}
