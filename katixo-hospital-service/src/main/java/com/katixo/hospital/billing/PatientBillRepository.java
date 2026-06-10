package com.katixo.hospital.billing;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientBillRepository extends BaseRepository<PatientBill> {

    @Query(value = "SELECT nextval('hospital.bill_seq')", nativeQuery = true)
    Long nextBillSequence();
}
