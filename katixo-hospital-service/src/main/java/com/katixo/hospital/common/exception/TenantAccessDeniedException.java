package com.katixo.hospital.common.exception;

public class TenantAccessDeniedException extends RuntimeException {
    public TenantAccessDeniedException(String message) {
        super(message);
    }

    public TenantAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
