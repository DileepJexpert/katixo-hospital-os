package com.katixo.hospital.ipd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ward", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ward extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private WardType wardType;

    public enum WardType {
        GENERAL, ICU, PRIVATE, SEMI_PRIVATE, EMERGENCY
    }
}
