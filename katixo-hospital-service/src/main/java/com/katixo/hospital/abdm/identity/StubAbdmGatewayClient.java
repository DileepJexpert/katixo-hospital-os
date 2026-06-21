package com.katixo.hospital.abdm.identity;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.common.exception.BusinessException;

/**
 * Placeholder gateway client used until the real NHA-wrapper-backed client is
 * wired. Every call fails loudly with {@code ABDM_GATEWAY_NOT_CONFIGURED} so the
 * app boots and the ABHA endpoints exist, but a misconfigured/incomplete
 * deployment can't silently pretend to talk to ABDM. Replaced automatically once
 * a real {@link AbdmGatewayClient} bean is on the classpath
 * (see {@code AbdmGatewayConfig}).
 *
 * <p>Note: the {@code recordAbha} / scan-and-share path in {@code AbhaService}
 * does NOT go through the gateway, so storing an ABHA captured from a QR works
 * even with this stub in place.
 */
public class StubAbdmGatewayClient implements AbdmGatewayClient {

    private static final String CODE = "ABDM_GATEWAY_NOT_CONFIGURED";
    private static final String MSG =
            "ABDM gateway client is not wired yet — integrate the NHA ABDM wrapper before using OTP enrolment/linking.";

    @Override
    public OtpInit initiateAadhaarOtp(AbdmSettings settings, String aadhaar) {
        throw new BusinessException(CODE, MSG);
    }

    @Override
    public AbhaProfile verifyAadhaarOtp(AbdmSettings settings, String txnId, String otp, String mobile) {
        throw new BusinessException(CODE, MSG);
    }

    @Override
    public OtpInit initiateAbhaLoginOtp(AbdmSettings settings, String abhaIdOrAddress) {
        throw new BusinessException(CODE, MSG);
    }

    @Override
    public AbhaProfile verifyAbhaLoginOtp(AbdmSettings settings, String txnId, String otp) {
        throw new BusinessException(CODE, MSG);
    }
}
