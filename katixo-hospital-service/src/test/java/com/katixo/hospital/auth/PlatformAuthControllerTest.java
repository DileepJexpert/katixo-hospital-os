package com.katixo.hospital.auth;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.PlatformOperator;
import com.katixo.hospital.tenant.PlatformOperatorDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAuthControllerTest {

    @Mock PlatformOperatorDao operatorDao;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private JwtTokenProvider jwtTokenProvider;
    private PlatformAuthController controller;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "test-secret-key-min-256-bits-long-for-platform-auth-tests!");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 86400000L);
        controller = new PlatformAuthController(operatorDao, encoder, jwtTokenProvider);
    }

    private PlatformOperator op(String status) {
        return new PlatformOperator(1L, "platformadmin", encoder.encode("platform123"),
                "Platform Operator", status);
    }

    @Test
    void login_issues_a_tenantless_platform_admin_token() {
        when(operatorDao.findByUsername("platformadmin"))
                .thenReturn(Optional.of(op(PlatformOperator.STATUS_ACTIVE)));

        ApiResponse<Map<String, Object>> body =
                controller.login(new PlatformAuthController.LoginRequest("platformadmin", "platform123")).getBody();
        assertNotNull(body);
        Map<String, Object> data = body.getData();
        String token = (String) data.get("token");
        assertNotNull(token);

        JwtClaims claims = jwtTokenProvider.getClaimsFromToken(token);
        assertEquals(List.of("PLATFORM_ADMIN"), claims.getRoles());
        assertEquals("platformadmin", claims.getUsername());
        // Platform operators have no tenant.
        assertNull(claims.getTenantId());

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertEquals("PLATFORM_ADMIN", user.get("role"));
    }

    @Test
    void login_rejects_a_wrong_password() {
        when(operatorDao.findByUsername("platformadmin"))
                .thenReturn(Optional.of(op(PlatformOperator.STATUS_ACTIVE)));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(new PlatformAuthController.LoginRequest("platformadmin", "wrong")));
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void login_rejects_an_inactive_operator() {
        when(operatorDao.findByUsername("platformadmin"))
                .thenReturn(Optional.of(op("DISABLED")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(new PlatformAuthController.LoginRequest("platformadmin", "platform123")));
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void me_rejects_a_non_platform_token() {
        // A hospital staff token (no PLATFORM_ADMIN role) must not pass the platform /me.
        JwtClaims staff = JwtClaims.builder()
                .tenantId("demo-tenant").username("dr.house").roles(List.of("DOCTOR")).build();
        String token = jwtTokenProvider.generateToken(staff);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.me("Bearer " + token));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void me_returns_the_operator_for_a_platform_token() {
        when(operatorDao.findByUsername("platformadmin"))
                .thenReturn(Optional.of(op(PlatformOperator.STATUS_ACTIVE)));
        JwtClaims claims = JwtClaims.builder()
                .userId("platform:platformadmin").username("platformadmin")
                .roles(List.of("PLATFORM_ADMIN")).build();
        String token = jwtTokenProvider.generateToken(claims);

        ApiResponse<Map<String, Object>> body = controller.me("Bearer " + token).getBody();
        assertNotNull(body);
        Map<String, Object> user = body.getData();
        assertEquals("platformadmin", user.get("username"));
        assertTrue("PLATFORM_ADMIN".equals(user.get("role")));
    }
}
