package com.katixo.hospital.notification;

/** Outcome of a provider send attempt. */
public record SendResult(boolean sent, String providerMessageId, String error) {

    public static SendResult ok(String providerMessageId) {
        return new SendResult(true, providerMessageId, null);
    }

    public static SendResult failed(String error) {
        return new SendResult(false, null, error);
    }
}
