package com.katixo.hospital.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * RFC 6238 time-based one-time passwords (TOTP, SHA-1, 6 digits, 30s step) with
 * a ±1 step tolerance — compatible with Google Authenticator / Authy. No
 * external dependency: base32 + HMAC-SHA1 are implemented here.
 */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1; // accept the previous/next step too
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();

    /** A fresh random base32 secret (160 bits) for enrollment. */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** otpauth:// URI the authenticator app reads (usually via QR). */
    public String otpAuthUri(String secret, String account, String issuer) {
        String label = url(issuer) + ":" + url(account);
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + url(issuer) + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Current 6-digit code for a secret (for tests / display). */
    public String currentCode(String secret, long timeMillis) {
        return codeForCounter(secret, timeMillis / 1000 / STEP_SECONDS);
    }

    public boolean verify(String secret, String code) {
        return verify(secret, code, System.currentTimeMillis());
    }

    /** Verifies a code within the ±WINDOW steps around {@code timeMillis}. */
    public boolean verify(String secret, String code, long timeMillis) {
        if (secret == null || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.length() != DIGITS) {
            return false;
        }
        long counter = timeMillis / 1000 / STEP_SECONDS;
        for (int w = -WINDOW; w <= WINDOW; w++) {
            if (constantTimeEquals(codeForCounter(secret, counter + w), trimmed)) {
                return true;
            }
        }
        return false;
    }

    private String codeForCounter(String secret, long counter) {
        byte[] key = base32Decode(secret);
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmacSha1(key, msg);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    private byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 failure", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    // ---- base32 (RFC 4648, no padding) ----

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(BASE32.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32.charAt(index));
        }
        return sb.toString();
    }

    private byte[] base32Decode(String s) {
        String clean = s.trim().replace("=", "").toUpperCase().replace(" ", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32.indexOf(clean.charAt(i));
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private String url(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
