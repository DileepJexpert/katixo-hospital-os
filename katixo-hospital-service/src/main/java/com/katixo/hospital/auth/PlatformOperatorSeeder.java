package com.katixo.hospital.auth;

import com.katixo.hospital.tenant.PlatformOperatorDao;
import com.katixo.hospital.tenant.TenantBootstrap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo platform operator on startup (non-prod only) so the platform
 * console is usable out of the box: {@code platformadmin / platform123}. Runs
 * after {@link TenantBootstrap} so the platform schema exists. No tenant context
 * is needed — the operator lives in the fixed platform schema.
 */
@Component
@Profile("!prod")
@Order(TenantBootstrap.ORDER + 10)
@RequiredArgsConstructor
@Slf4j
public class PlatformOperatorSeeder implements CommandLineRunner {

    private static final String DEMO_USERNAME = "platformadmin";

    private final PlatformOperatorDao operatorDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (operatorDao.existsByUsername(DEMO_USERNAME)) {
            return;
        }
        operatorDao.insert(DEMO_USERNAME, passwordEncoder.encode("platform123"), "Platform Operator");
        log.info("Seeded dev platform operator '{}'", DEMO_USERNAME);
    }
}
