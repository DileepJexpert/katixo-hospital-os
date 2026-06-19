package com.katixo.hospital.tenant;

/**
 * A platform operator login (control-plane). Lives in {@code platform.platform_operator},
 * never in a tenant schema — see {@link PlatformOperatorDao}.
 */
public record PlatformOperator(Long id, String username, String passwordHash,
                               String displayName, String status) {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
