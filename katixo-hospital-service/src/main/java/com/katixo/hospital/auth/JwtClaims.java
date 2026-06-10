package com.katixo.hospital.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtClaims {
    private String tenantId;      // VARCHAR(50)
    private String hospitalGroupId; // Can be parsed to Long
    private String branchId;       // Can be parsed to Long
    private String userId;         // VARCHAR(100)
    private String username;
}
