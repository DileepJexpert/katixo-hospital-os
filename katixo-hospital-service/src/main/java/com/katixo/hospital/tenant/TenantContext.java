package com.katixo.hospital.tenant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TenantContext {
    private final String tenantId;       // VARCHAR(50) from schema
    private final String hospitalGroupId; // Can be parsed to Long if needed
    private final String branchId;        // Can be parsed to Long if needed
    private final String userId;          // VARCHAR(100) from schema
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
