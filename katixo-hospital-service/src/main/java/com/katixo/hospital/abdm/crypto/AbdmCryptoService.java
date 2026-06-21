package com.katixo.hospital.abdm.crypto;

import com.katixo.hospital.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ABDM consent data-exchange crypto: ephemeral X25519 ECDH key agreement →
 * HKDF-SHA256 key derivation → AES-256-GCM payload encryption. Both HIP (sender)
 * and HIU (receiver) generate an ephemeral key pair + a random nonce, exchange
 * the public keys and nonces, derive the same AES key, and the encrypted FHIR
 * bundle flows over the data-push channel.
 *
 * <p><b>Spec note:</b> ABDM's "Cryptography" appendix pins the exact key/IV
 * derivation (salt = combine(senderNonce, receiverNonce), HKDF info, IV length).
 * The derivation here (salt = XOR of nonces, IV = first 12 bytes of salt) is the
 * common interpretation and MUST be confirmed bit-for-bit against the sandbox
 * crypto spec version before go-live — it's isolated in this one class for that.
 */
@Service
public class AbdmCryptoService {

    private static final String CURVE = "X25519";
    private static final int AES_KEY_BYTES = 32;     // AES-256
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int NONCE_BYTES = 32;

    @PostConstruct
    void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** An ephemeral key pair + nonce; share {@link #publicKeyBase64}/{@link #nonceBase64} with the peer. */
    @Getter
    public static class KeyMaterial {
        private final KeyPair keyPair;
        private final String publicKeyBase64;
        private final String nonceBase64;

        KeyMaterial(KeyPair keyPair, String publicKeyBase64, String nonceBase64) {
            this.keyPair = keyPair;
            this.publicKeyBase64 = publicKeyBase64;
            this.nonceBase64 = nonceBase64;
        }
    }

    /** Encrypted payload bundle to hand to the data-push transport. */
    public record Encrypted(String cipherTextBase64, String senderPublicKeyBase64, String nonceBase64) {}

    public KeyMaterial generateKeyMaterial() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(CURVE, BouncyCastleProvider.PROVIDER_NAME);
            KeyPair kp = kpg.generateKeyPair();
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            return new KeyMaterial(kp,
                    Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (Exception e) {
            throw new BusinessException("ABDM_CRYPTO_KEYGEN_FAILED", "X25519 key generation failed: " + e.getMessage());
        }
    }

    /** Encrypt a FHIR payload for the peer using our private key + the peer's public key/nonce. */
    public Encrypted encrypt(byte[] plaintext, KeyMaterial own, String peerPublicKeyBase64, String peerNonceBase64) {
        byte[] shared = sharedSecret(own.getKeyPair().getPrivate(), peerPublicKeyBase64);
        byte[] salt = xor(decode(own.getNonceBase64()), decode(peerNonceBase64));
        byte[] aesKey = hkdf(shared, salt);
        byte[] iv = firstBytes(salt, IV_BYTES);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext);
            return new Encrypted(Base64.getEncoder().encodeToString(ct),
                    own.getPublicKeyBase64(), own.getNonceBase64());
        } catch (Exception e) {
            throw new BusinessException("ABDM_CRYPTO_ENCRYPT_FAILED", "AES-GCM encrypt failed: " + e.getMessage());
        }
    }

    /** Decrypt a payload received from the peer using our private key + the peer's public key/nonce. */
    public byte[] decrypt(String cipherTextBase64, KeyMaterial own, String peerPublicKeyBase64, String peerNonceBase64) {
        byte[] shared = sharedSecret(own.getKeyPair().getPrivate(), peerPublicKeyBase64);
        byte[] salt = xor(decode(own.getNonceBase64()), decode(peerNonceBase64));
        byte[] aesKey = hkdf(shared, salt);
        byte[] iv = firstBytes(salt, IV_BYTES);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(decode(cipherTextBase64));
        } catch (Exception e) {
            throw new BusinessException("ABDM_CRYPTO_DECRYPT_FAILED", "AES-GCM decrypt failed: " + e.getMessage());
        }
    }

    private byte[] sharedSecret(PrivateKey priv, String peerPublicKeyBase64) {
        try {
            KeyFactory kf = KeyFactory.getInstance(CURVE, BouncyCastleProvider.PROVIDER_NAME);
            PublicKey peer = kf.generatePublic(new X509EncodedKeySpec(decode(peerPublicKeyBase64)));
            KeyAgreement ka = KeyAgreement.getInstance(CURVE, BouncyCastleProvider.PROVIDER_NAME);
            ka.init(priv);
            ka.doPhase(peer, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new BusinessException("ABDM_CRYPTO_ECDH_FAILED", "X25519 ECDH failed: " + e.getMessage());
        }
    }

    private byte[] hkdf(byte[] ikm, byte[] salt) {
        HKDFBytesGenerator gen = new HKDFBytesGenerator(new SHA256Digest());
        gen.init(new HKDFParameters(ikm, salt, "abdm".getBytes()));
        byte[] out = new byte[AES_KEY_BYTES];
        gen.generateBytes(out, 0, out.length);
        return out;
    }

    private static byte[] xor(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    private static byte[] firstBytes(byte[] src, int n) {
        byte[] out = new byte[n];
        System.arraycopy(src, 0, out, 0, Math.min(n, src.length));
        return out;
    }

    private static byte[] decode(String b64) {
        return Base64.getDecoder().decode(b64);
    }
}
