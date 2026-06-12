package com.katixo.hospital.discharge;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One checklist item for an admission's discharge. Whether an incomplete
 * item BLOCKS discharge or merely WARNS is not stored here — it is decided
 * at evaluation time by the IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS policy
 * (CLAUDE.md: configurable behavior lives in the policy engine).
 */
@Entity
@Table(name = "discharge_checklist_item", indexes = {
        @Index(name = "idx_discharge_checklist_admission", columnList = "admission_id"),
        @Index(name = "idx_discharge_checklist_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DischargeChecklistItem extends BaseEntity {

    @Column(nullable = false)
    private Long admissionId;

    @Column(nullable = false, length = 50)
    private String itemCode;

    @Column(nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column
    private Long completedBy;

    @Column
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
