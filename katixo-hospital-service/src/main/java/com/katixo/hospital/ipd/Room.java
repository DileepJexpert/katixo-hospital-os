package com.katixo.hospital.ipd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "ward_id", "room_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Room extends BaseEntity {

    @Column(nullable = false)
    private Long wardId;

    @Column(nullable = false, length = 20)
    private String roomNumber;
}
