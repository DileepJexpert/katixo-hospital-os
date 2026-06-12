package com.katixo.hospital.nursing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nursing_indent_item", indexes = {
        @Index(name = "idx_nursing_indent_item_indent", columnList = "indent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NursingIndentItem extends BaseEntity {

    @Column(nullable = false)
    private Long indentId;

    /** ERP item SKU — the pharmacy resolves it against the Katasticho item master. */
    @Column(nullable = false, length = 50)
    private String medicineCode;

    @Column(nullable = false, length = 255)
    private String medicineName;

    @Column(nullable = false)
    private Integer quantity;

    /** Approval requirement is decided per category by the policy engine. */
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ItemCategory itemCategory = ItemCategory.MEDICINE;

    public enum ItemCategory {
        MEDICINE, CONSUMABLE, IMPLANT, NARCOTIC
    }
}
