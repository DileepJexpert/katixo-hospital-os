package com.katixo.hospital.ot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnesthesiaRecordRepository extends JpaRepository<AnesthesiaRecord, Long> {
    AnesthesiaRecord findByOtBookingId(Long otBookingId);
}
