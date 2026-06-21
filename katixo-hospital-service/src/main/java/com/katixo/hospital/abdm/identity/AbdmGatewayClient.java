package com.katixo.hospital.abdm.identity;

import com.katixo.hospital.abdm.config.AbdmSettings;

/**
 * Thin contract over the ABDM gateway for ABHA identity flows. The real
 * implementation wraps the NHA open-source ABDM client (gateway session, crypto,
 * OTP, enrolment) and is dropped in as a {@code @Component} — when present it
 * replaces {@code StubAbdmGatewayClient} (registered
 * {@code @ConditionalOnMissingBean}). Kept deliberately small so M1 can ship
 * before the full HIP/HIU machinery exists.
 *
 * <p>All calls take the per-tenant {@link AbdmSettings} (env + credentials).
 */
public interface AbdmGatewayClient {

    /** Start Aadhaar-OTP enrolment; returns a transaction id to verify against. */
    OtpInit initiateAadhaarOtp(AbdmSettings settings, String aadhaar);

    /** Complete Aadhaar-OTP enrolment → a created ABHA profile. */
    AbhaProfile verifyAadhaarOtp(AbdmSettings settings, String txnId, String otp, String mobile);

    /** Start login/verify OTP for an existing ABHA (number or address). */
    OtpInit initiateAbhaLoginOtp(AbdmSettings settings, String abhaIdOrAddress);

    /** Complete login/verify → the linked ABHA profile. */
    AbhaProfile verifyAbhaLoginOtp(AbdmSettings settings, String txnId, String otp);

    /** OTP initiation result. */
    record OtpInit(String txnId, String maskedTarget) {}

    /** Resolved ABHA identity + demographics (for match/auto-fill). */
    record AbhaProfile(String abhaNumber, String abhaAddress, String name,
                       String gender, String dateOfBirth, String mobile) {}
}
