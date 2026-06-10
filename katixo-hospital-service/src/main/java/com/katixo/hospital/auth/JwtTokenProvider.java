package com.katixo.hospital.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${katixo.security.jwt-secret}")
    private String jwtSecret;

    @Value("${katixo.security.jwt-expiration}")
    private long jwtExpirationMs;

    private static final String TENANT_ID = "tenantId";
    private static final String HOSPITAL_GROUP_ID = "hospitalGroupId";
    private static final String BRANCH_ID = "branchId";
    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";
    private static final String ROLES = "roles";

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public JwtClaims getClaimsFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            @SuppressWarnings("unchecked")
            java.util.List<String> roles = claims.get(ROLES, java.util.List.class);

            return JwtClaims.builder()
                    .tenantId(claims.get(TENANT_ID, String.class))
                    .hospitalGroupId(claims.get(HOSPITAL_GROUP_ID, String.class))
                    .branchId(claims.get(BRANCH_ID, String.class))
                    .userId(claims.get(USER_ID, String.class))
                    .username(claims.get(USERNAME, String.class))
                    .roles(roles)
                    .build();
        } catch (JwtException e) {
            throw new IllegalArgumentException("Failed to parse JWT: " + e.getMessage(), e);
        }
    }

    public String generateToken(JwtClaims claims) {
        return Jwts.builder()
                .claim(TENANT_ID, claims.getTenantId())
                .claim(HOSPITAL_GROUP_ID, claims.getHospitalGroupId())
                .claim(BRANCH_ID, claims.getBranchId())
                .claim(USER_ID, claims.getUserId())
                .claim(USERNAME, claims.getUsername())
                .claim(ROLES, claims.getRoles())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(signingKey())
                .compact();
    }
}
