package com.katixo.hospital.ot;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An operating theatre / OT room that surgeries are scheduled into. */
@Entity
@Table(name = "ot_room", indexes = {
        @Index(name = "idx_ot_room_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
public class OtRoom extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 150)
    private String location;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
