package com.katixo.hospital.ot;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "surgery_note", indexes = {
        @Index(name = "idx_surgery_note_booking", columnList = "ot_booking_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ot_booking_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SurgeryNote extends BaseEntity {

    @Column(nullable = false)
    private Long otBookingId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String procedureDetails;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String implantsUsed;

    @Column(columnDefinition = "TEXT")
    private String complications;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Long documentedBy;

    @Column(nullable = false)
    private LocalDateTime documentedAt;
}
