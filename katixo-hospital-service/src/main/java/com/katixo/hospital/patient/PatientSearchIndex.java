package com.katixo.hospital.patient;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_search_index", indexes = {
        @Index(name = "idx_patient_search_tenant", columnList = "tenant_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSearchIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, unique = true, updatable = false)
    private Long patientId;

    @Column(nullable = false, length = 300)
    private String fullName;

    @Column(length = 15)
    private String mobile;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String uhid;

    @Column(columnDefinition = "TEXT")
    private String identifiersText;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime indexedAt;
}
