package com.katixo.hospital.prescription;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prescription", indexes = {
        @Index(name = "idx_prescription_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_prescription_visit", columnList = "visit_id,is_latest"),
        @Index(name = "idx_prescription_patient", columnList = "patient_id,created_at"),
        @Index(name = "idx_prescription_status", columnList = "prescription_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Prescription extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String prescriptionNumber;

    @Column(nullable = false)
    private Long visitId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long doctorId;

    @Column(nullable = false)
    private Integer version = 1;

    @Column
    private Long parentPrescriptionId;

    @Column(nullable = false)
    private Boolean isLatest = true;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PrescriptionStatus prescriptionStatus = PrescriptionStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private LocalDateTime dispensedAt;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PrescriptionItem> items = new ArrayList<>();

    public enum PrescriptionStatus {
        ACTIVE, DISPENSED, CANCELLED, SUPERSEDED
    }

    public void addItem(PrescriptionItem item) {
        item.setPrescription(this);
        this.items.add(item);
    }

    public void clearItems() {
        this.items.forEach(i -> i.setPrescription(null));
        this.items.clear();
    }
}
