package com.katixo.hospital.auth;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
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

    public boolean validateToken(String token) {
        try {
            var secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), 0,
                    jwtSecret.getBytes(StandardCharsets.UTF_8).length, "HmacSHA256");
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public JwtClaims getClaimsFromToken(String token) {
        try {
            var secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), 0,
                    jwtSecret.getBytes(StandardCharsets.UTF_8).length, "HmacSHA256");
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return JwtClaims.builder()
                    .tenantId(claims.get(TENANT_ID, String.class))
                    .hospitalGroupId(claims.get(HOSPITAL_GROUP_ID, String.class))
                    .branchId(claims.get(BRANCH_ID, String.class))
                    .userId(claims.get(USER_ID, String.class))
                    .username(claims.get(USERNAME, String.class))
                    .build();
        } catch (JwtException e) {
            throw new RuntimeException("Failed to parse JWT: " + e.getMessage(), e);
        }
    }

    public String generateToken(JwtClaims claims) {
        var secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), 0,
                jwtSecret.getBytes(StandardCharsets.UTF_8).length, "HmacSHA256");

        return Jwts.builderBuilder()
                .claim(TENANT_ID, claims.getTenantId())
                .claim(HOSPITAL_GROUP_ID, claims.getHospitalGroupId())
                .claim(BRANCH_ID, claims.getBranchId())
                .claim(USER_ID, claims.getUserId())
                .claim(USERNAME, claims.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
