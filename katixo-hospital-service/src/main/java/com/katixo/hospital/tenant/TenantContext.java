package com.katixo.hospital.tenant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class TenantContext {
    private final UUID tenantId;
    private final UUID hospitalGroupId;
    private final UUID branchId;
    private final UUID userId;
    private final String username;

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext get() {
        TenantContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("TenantContext not initialized");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
