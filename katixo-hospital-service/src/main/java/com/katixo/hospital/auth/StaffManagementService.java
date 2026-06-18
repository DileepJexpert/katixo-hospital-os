package com.katixo.hospital.auth;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Staff / user administration: an ADMIN creates and maintains the hospital's own
 * staff logins (doctors, nurses, pharmacists, billing clerks, …). This is the
 * production replacement for the dev-only {@code DevUserSeeder} — a real hospital
 * onboards its people, deactivates leavers and resets passwords through here.
 *
 * <p>Logins live in {@code staff_user_ref}; the same row is what {@code AuthController}
 * checks at login. New rows get an {@code authUserId} equal to their generated id so
 * the JWT's {@code userId} claim (used as createdBy/updatedBy everywhere) is valid.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StaffManagementService {

    /**
     * Roles an ADMIN may assign. SUPER_ADMIN is deliberately excluded — it is a
     * testing/owner superuser granted every authority, not an onboardable role.
     */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            "ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "LAB_TECH", "BILLING", "FRONT_DESK");

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StaffUser> list(boolean includeInactive) {
        var ctx = TenantContext.get();
        List<StaffUser> all = staffUserRepository.findByTenantIdAndBranchIdOrderByName(
                ctx.getTenantId(), branchId());
        if (includeInactive) {
            return all;
        }
        return all.stream().filter(u -> "ACTIVE".equals(u.getStatus())).toList();
    }

    @Transactional(readOnly = true)
    public StaffUser get(Long id) {
        return getOwned(id);
    }

    public StaffUser create(String name, String username, String password, String role,
                            String staffCode, String specialisation) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("STAFF_NAME_REQUIRED", "Staff name is required");
        }
        if (username == null || username.isBlank()) {
            throw new BusinessException("STAFF_USERNAME_REQUIRED", "A login username is required");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("STAFF_PASSWORD_WEAK", "Password must be at least 6 characters");
        }
        String normalisedRole = normaliseRole(role);
        String normalisedUsername = username.trim().toLowerCase();
        if (staffUserRepository.existsByUsername(normalisedUsername)) {
            throw new BusinessException("STAFF_USERNAME_EXISTS", "Username '" + normalisedUsername + "' is taken");
        }

        var ctx = TenantContext.get();
        StaffUser user = new StaffUser();
        user.setTenantId(ctx.getTenantId());
        user.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        user.setBranchId(branchId());
        user.setName(name.trim());
        user.setUsername(normalisedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(normalisedRole);
        user.setStaffCode(staffCode == null || staffCode.isBlank() ? null : staffCode.trim());
        user.setSpecialisation(specialisation == null || specialisation.isBlank() ? null : specialisation.trim());
        user.setStatus("ACTIVE");
        // Placeholder; replaced with the real id below so the JWT userId claim is valid.
        user.setAuthUserId("pending");
        StaffUser saved = staffUserRepository.save(user);
        saved.setAuthUserId(String.valueOf(saved.getId()));
        saved = staffUserRepository.save(saved);

        auditService.audit("StaffUser", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("username", saved.getUsername(), "role", saved.getRole()),
                UUID.randomUUID().toString());
        log.info("Staff user '{}' created with role {}", saved.getUsername(), saved.getRole());
        return saved;
    }

    public StaffUser update(Long id, String name, String role, String staffCode, String specialisation) {
        StaffUser user = getOwned(id);
        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }
        if (role != null && !role.isBlank()) {
            user.setRole(normaliseRole(role));
        }
        if (staffCode != null) {
            user.setStaffCode(staffCode.isBlank() ? null : staffCode.trim());
        }
        if (specialisation != null) {
            user.setSpecialisation(specialisation.isBlank() ? null : specialisation.trim());
        }
        StaffUser saved = staffUserRepository.save(user);
        auditService.audit("StaffUser", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("username", saved.getUsername(), "role", saved.getRole()),
                UUID.randomUUID().toString());
        return saved;
    }

    public StaffUser setStatus(Long id, boolean active) {
        StaffUser user = getOwned(id);
        if (!active && user.getId().equals(currentStaffId())) {
            throw new BusinessException("STAFF_SELF_DEACTIVATE", "You cannot deactivate your own login");
        }
        user.setStatus(active ? "ACTIVE" : "INACTIVE");
        StaffUser saved = staffUserRepository.save(user);
        auditService.audit("StaffUser", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("username", saved.getUsername(), "status", saved.getStatus()),
                UUID.randomUUID().toString());
        return saved;
    }

    public StaffUser resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("STAFF_PASSWORD_WEAK", "Password must be at least 6 characters");
        }
        StaffUser user = getOwned(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        StaffUser saved = staffUserRepository.save(user);
        auditService.audit("StaffUser", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("username", saved.getUsername(), "action", "PASSWORD_RESET"),
                UUID.randomUUID().toString());
        log.info("Password reset for staff user '{}'", saved.getUsername());
        return saved;
    }

    // ---------------- helpers ----------------

    private String normaliseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BusinessException("STAFF_ROLE_REQUIRED", "A role is required");
        }
        String r = role.trim().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(r)) {
            throw new BusinessException("STAFF_ROLE_INVALID",
                    "Role must be one of " + ASSIGNABLE_ROLES);
        }
        return r;
    }

    private StaffUser getOwned(Long id) {
        var ctx = TenantContext.get();
        StaffUser user = staffUserRepository.findById(id)
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Staff user not found: " + id));
        // Defence in depth on top of schema isolation.
        if (!ctx.getTenantId().equals(user.getTenantId()) || !branchId().equals(user.getBranchId())) {
            throw new BusinessException("STAFF_NOT_FOUND", "Staff user not found: " + id);
        }
        return user;
    }

    private Long currentStaffId() {
        var ctx = TenantContext.get();
        return staffUserRepository.findByUsernameAndStatus(ctx.getUsername(), "ACTIVE")
                .map(StaffUser::getId).orElse(null);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }
}
