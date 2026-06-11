package com.katixo.hospital.auth;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Maps to the existing staff_user_ref foundation table.
 * Credentials (username/password_hash) added by V1_010 for built-in login
 * until shared ERP auth is wired.
 */
@Entity
@Table(name = "staff_user_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false)
    private Long hospitalGroupId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 100)
    private String authUserId;

    @Column(length = 20)
    private String staffCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(length = 100)
    private String username;

    @Column(length = 100)
    private String passwordHash;

    @Column(length = 100)
    private String specialisation;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
