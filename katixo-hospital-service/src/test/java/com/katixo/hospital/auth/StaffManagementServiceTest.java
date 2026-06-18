package com.katixo.hospital.auth;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffManagementServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock StaffUserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    private StaffManagementService service;

    @BeforeEach
    void setUp() {
        service = new StaffManagementService(repository, passwordEncoder, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(passwordEncoder.encode(any())).thenReturn("ENC");
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            StaffUser u = inv.getArgument(0);
            if (u.getId() == null) ReflectionTestUtils.setField(u, "id", 42L);
            return u;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createSetsAuthUserIdToGeneratedIdAndHashesPassword() {
        when(repository.existsByUsername("doctor.new")).thenReturn(false);
        StaffUser created = service.create("Dr New", "Doctor.New", "secret1", "doctor", "D-01", "Cardiology");
        assertEquals("42", created.getAuthUserId());
        assertEquals("doctor.new", created.getUsername());
        assertEquals("DOCTOR", created.getRole());
        assertEquals("ENC", created.getPasswordHash());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(repository.existsByUsername("taken")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("X", "taken", "secret1", "NURSE", null, null));
        assertEquals("STAFF_USERNAME_EXISTS", ex.getCode());
    }

    @Test
    void createRejectsWeakPassword() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("X", "user1", "123", "NURSE", null, null));
        assertEquals("STAFF_PASSWORD_WEAK", ex.getCode());
    }

    @Test
    void createRejectsSuperAdminRole() {
        lenient().when(repository.existsByUsername(any())).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("X", "user1", "secret1", "SUPER_ADMIN", null, null));
        assertEquals("STAFF_ROLE_INVALID", ex.getCode());
    }

    @Test
    void createRejectsUnknownRole() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("X", "user1", "secret1", "WIZARD", null, null));
        assertEquals("STAFF_ROLE_INVALID", ex.getCode());
    }

    @Test
    void deactivateBlocksSelf() {
        StaffUser me = staff(9L, "admin", "ACTIVE");
        when(repository.findById(9L)).thenReturn(Optional.of(me));
        when(repository.findByUsernameAndStatus("admin", "ACTIVE")).thenReturn(Optional.of(me));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setStatus(9L, false));
        assertEquals("STAFF_SELF_DEACTIVATE", ex.getCode());
    }

    @Test
    void deactivateOthersWorks() {
        StaffUser other = staff(5L, "nurse1", "ACTIVE");
        when(repository.findById(5L)).thenReturn(Optional.of(other));
        when(repository.findByUsernameAndStatus("admin", "ACTIVE"))
                .thenReturn(Optional.of(staff(9L, "admin", "ACTIVE")));
        StaffUser saved = service.setStatus(5L, false);
        assertEquals("INACTIVE", saved.getStatus());
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resetPassword(5L, "x"));
        assertEquals("STAFF_PASSWORD_WEAK", ex.getCode());
    }

    @Test
    void resetPasswordHashesNewSecret() {
        StaffUser other = staff(5L, "nurse1", "ACTIVE");
        when(repository.findById(5L)).thenReturn(Optional.of(other));
        StaffUser saved = service.resetPassword(5L, "brandnew");
        assertEquals("ENC", saved.getPasswordHash());
    }

    @Test
    void listExcludesInactiveWhenAsked() {
        when(repository.findByTenantIdAndBranchIdOrderByName(TENANT, 1L))
                .thenReturn(List.of(staff(1L, "a", "ACTIVE"), staff(2L, "b", "INACTIVE")));
        assertEquals(1, service.list(false).size());
        assertEquals(2, service.list(true).size());
    }

    @Test
    void getRejectsCrossTenant() {
        StaffUser foreign = staff(7L, "x", "ACTIVE");
        foreign.setTenantId("other-tenant");
        when(repository.findById(7L)).thenReturn(Optional.of(foreign));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.get(7L));
        assertEquals("STAFF_NOT_FOUND", ex.getCode());
    }

    private StaffUser staff(Long id, String username, String status) {
        StaffUser u = new StaffUser();
        ReflectionTestUtils.setField(u, "id", id);
        u.setTenantId(TENANT);
        u.setHospitalGroupId(1L);
        u.setBranchId(1L);
        u.setUsername(username);
        u.setName(username);
        u.setRole("NURSE");
        u.setStatus(status);
        u.setAuthUserId(String.valueOf(id));
        return u;
    }
}
