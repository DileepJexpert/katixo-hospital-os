package com.katixo.hospital.ot;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "anesthesia_record", indexes = {
        @Index(name = "idx_anesthesia_record_booking", columnList = "ot_booking_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ot_booking_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnesthesiaRecord extends BaseEntity {

    @Column(nullable = false)
    private Long otBookingId;

    @Column(nullable = false, length = 50)
    private String anesthesiaType;

    @Column
    private LocalDateTime inductionTime;

    @Column
    private LocalDateTime reversalTime;

    @Column(columnDefinition = "TEXT")
    private String totalAgentsUsed;

    @Column(columnDefinition = "TEXT")
    private String vitalsNotes;

    @Column(columnDefinition = "TEXT")
    private String complications;

    @Column(columnDefinition = "TEXT")
    private String postOpNotes;

    @Column(nullable = false)
    private Long documentedBy;

    @Column(nullable = false)
    private LocalDateTime documentedAt;
}
