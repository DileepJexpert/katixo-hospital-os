package com.katixo.hospital.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds demo staff logins on startup (non-prod only) so the app is
 * usable out of the box. Passwords are BCrypt-encoded at runtime.
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DevUserSeeder implements CommandLineRunner {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    private record Seed(String username, String password, String name, String role, String authUserId) {
    }

    @Override
    public void run(String... args) {
        List<Seed> seeds = List.of(
                new Seed("admin", "admin123", "System Admin", "ADMIN", "1"),
                new Seed("reception", "desk123", "Front Desk", "FRONT_DESK", "2"),
                new Seed("doctor1", "pass123", "Dr. Sharma", "DOCTOR", "3"),
                new Seed("nurse1", "pass123", "Nurse Asha", "NURSE", "4"),
                new Seed("pharmacist1", "pass123", "Pharmacist Ravi", "PHARMACIST", "5"),
                new Seed("labtech1", "pass123", "Lab Tech Vinod", "LAB_TECH", "6"),
                new Seed("billing1", "pass123", "Billing Clerk", "BILLING", "7")
        );

        for (Seed seed : seeds) {
            if (staffUserRepository.existsByUsername(seed.username())) {
                continue;
            }
            StaffUser user = new StaffUser();
            user.setTenantId("demo-tenant");
            user.setHospitalGroupId(1L);
            user.setBranchId(1L);
            user.setAuthUserId(seed.authUserId());
            user.setName(seed.name());
            user.setRole(seed.role());
            user.setUsername(seed.username());
            user.setPasswordHash(passwordEncoder.encode(seed.password()));
            user.setStatus("ACTIVE");
            staffUserRepository.save(user);
            log.info("Seeded demo user '{}' with role {}", seed.username(), seed.role());
        }
    }
}
