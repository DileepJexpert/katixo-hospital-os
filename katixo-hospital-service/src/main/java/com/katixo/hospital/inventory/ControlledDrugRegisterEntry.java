package com.katixo.hospital.inventory;

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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One controlled-drug supply (Schedule H1 / X / NDPS), recorded in a separate
 * register per Drugs &amp; Cosmetics Rule 65 / the NDPS rules: prescriber, patient,
 * drug, quantity and date, retained for inspection. Append-only.
 */
@Entity
@Table(name = "controlled_drug_register", indexes = {
        @Index(name = "idx_cdr_tenant_date", columnList = "tenant_id,entry_date"),
        @Index(name = "idx_cdr_schedule", columnList = "tenant_id,drug_schedule")
})
@Getter
@Setter
@NoArgsConstructor
public class ControlledDrugRegisterEntry extends BaseEntity {

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "drug_schedule", nullable = false, length = 10)
    private Item.DrugSchedule drugSchedule;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_code", length = 50)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "batch_number", length = 60)
    private String batchNumber;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "sale_number", length = 40)
    private String saleNumber;

    @Column(name = "prescriber_name", length = 150)
    private String prescriberName;

    @Column(name = "prescriber_address", length = 255)
    private String prescriberAddress;
}
