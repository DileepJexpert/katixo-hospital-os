package com.katixo.hospital.nursing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "nursing_indent", indexes = {
        @Index(name = "idx_nursing_indent_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_nursing_indent_status", columnList = "indent_status"),
        @Index(name = "idx_nursing_indent_admission", columnList = "admission_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "indent_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NursingIndent extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String indentNumber;

    @Column
    private Long admissionId;

    @Column(nullable = false, length = 100)
    private String wardSection;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IndentStatus indentStatus = IndentStatus.PENDING;

    @Column(nullable = false)
    private Long requestedBy;

    @Column
    private Long approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum IndentStatus {
        PENDING, APPROVED, FULFILLED, REJECTED
    }
}
