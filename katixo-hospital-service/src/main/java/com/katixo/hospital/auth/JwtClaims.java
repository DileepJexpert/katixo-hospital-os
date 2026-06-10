package com.katixo.hospital.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtClaims {
    private String tenantId;        // VARCHAR(50)
    private String hospitalGroupId; // parsed to Long where needed
    private String branchId;        // parsed to Long where needed
    private String userId;          // VARCHAR(100)
    private String username;
    private List<String> roles;     // FRONT_DESK, DOCTOR, NURSE, ADMIN, ...
}
