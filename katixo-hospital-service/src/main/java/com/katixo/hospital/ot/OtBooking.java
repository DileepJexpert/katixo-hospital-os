package com.katixo.hospital.ot;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A scheduled surgery in an OT room: room + patient + surgeon + procedure + slot.
 * Lifecycle SCHEDULED → IN_PROGRESS → COMPLETED (or CANCELLED); the operative
 * note is captured at completion. No accounting here — OT service charges are
 * billed via the tariff/charge path on the patient's bill.
 */
@Entity
@Table(name = "ot_booking", indexes = {
        @Index(name = "idx_ot_booking_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_ot_booking_room_date", columnList = "tenant_id,ot_room_id,scheduled_date")
})
@Getter
@Setter
@NoArgsConstructor
public class OtBooking extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String bookingNumber;

    @Column(nullable = false)
    private Long otRoomId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long surgeonId;

    @Column(nullable = false, length = 200)
    private String procedureName;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OtStatus otStatus = OtStatus.SCHEDULED;

    @Column(length = 500)
    private String notes;

    /** Operative / post-op note captured on completion. */
    @Column(length = 2000)
    private String surgeryNotes;

    public enum OtStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
