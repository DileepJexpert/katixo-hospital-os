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
    private String tenantId;
    private String hospitalGroupId;
    private String branchId;
    private String userId;
    private String username;
}
