package com.katixo.hospital.lab;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_sample", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "barcode"}),
        @UniqueConstraint(columnNames = {"lab_order_item_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @Column(nullable = false, updatable = false)
    private Long labOrderItemId;

    @Column(nullable = false, length = 30, updatable = false)
    private String barcode;

    @Column(nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    private LabTestMaster.SpecimenType specimenType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime collectedAt = LocalDateTime.now();

    @Column(nullable = false, updatable = false)
    private Long collectedBy;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String collectionNotes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
