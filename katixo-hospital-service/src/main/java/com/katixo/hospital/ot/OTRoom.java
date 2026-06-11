package com.katixo.hospital.ot;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ot_room", indexes = {
        @Index(name = "idx_ot_room_tenant_branch", columnList = "tenant_id,branch_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "room_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTRoom extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String roomNumber;

    @Column(nullable = false, length = 100)
    private String roomName;

    @Column(length = 50)
    private String roomType;

    @Column
    private Integer capacity;

    @Column(columnDefinition = "TEXT")
    private String equipmentList;
}
