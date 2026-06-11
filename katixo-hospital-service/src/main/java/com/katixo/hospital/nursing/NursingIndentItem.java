package com.katixo.hospital.nursing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "nursing_indent_item", indexes = {
        @Index(name = "idx_nursing_indent_item_status", columnList = "tenant_id,branch_id,item_status"),
        @Index(name = "idx_nursing_indent_item_indent", columnList = "nursing_indent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NursingIndentItem extends BaseEntity {

    @Column(nullable = false)
    private Long nursingIndentId;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ItemType itemType;

    @Column(length = 50)
    private String itemCode;

    @Column(nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ItemStatus itemStatus = ItemStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    public enum ItemType {
        CONSUMABLE, EQUIPMENT, MEDICATION
    }

    public enum ItemStatus {
        PENDING, APPROVED, FULFILLED, REJECTED
    }
}
