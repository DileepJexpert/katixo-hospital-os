package com.katixo.hospital.ot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SurgeryNoteRepository extends JpaRepository<SurgeryNote, Long> {
    SurgeryNote findByOtBookingId(Long otBookingId);
}
