package com.eu.habbo.networking.gameserver.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public final class WsSessionCrypto {

    public static final int  HANDSHAKE_MAGIC   = 0xC0DEC0DE;
    public static final byte TYPE_SERVER_HELLO = 0x01;
    public static final byte TYPE_CLIENT_HELLO = 0x02;

    public static final String HKDF_INFO = "nitro-ws-v1";
    public static final int    AES_KEY_LEN = 32;
    public static final int    NONCE_LEN   = 12;
    public static final int    GCM_TAG_BITS = 128;

    private static final SecureRandom RNG = new SecureRandom();

    private WsSessionCrypto() {}

    public static KeyPair generateEphemeralKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), RNG);
        return kpg.generateKeyPair();
    }

    public static byte[] encodePublicKeySpki(PublicKey publicKey) {
        return publicKey.getEncoded();
    }

    public static PublicKey decodePublicKeySpki(byte[] spki) throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(new X509EncodedKeySpec(spki));
    }

    public static byte[] deriveSharedSecret(PrivateKey ourPrivate, PublicKey theirPublic) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ourPrivate);
        ka.doPhase(theirPublic, true);
        return ka.generateSecret();
    }

    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) throws GeneralSecurityException {
        if (salt == null || salt.length == 0) salt = new byte[32];

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);

        int hashLen = 32;
        int n = (outLen + hashLen - 1) / hashLen;
        if (n > 255) throw new GeneralSecurityException("HKDF output too long");

        var okm = new ByteArrayOutputStream();
        byte[] t = new byte[0];

        for (int i = 1; i <= n; i++) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(t);
            if (info != null) mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            okm.write(t, 0, t.length);
        }

        byte[] result = okm.toByteArray();
        return (result.length == outLen) ? result : Arrays.copyOf(result, outLen);
    }

    public static byte[] deriveAesKey(byte[] sharedSecret) throws GeneralSecurityException {
        return hkdfSha256(sharedSecret, null, HKDF_INFO.getBytes(StandardCharsets.UTF_8), AES_KEY_LEN);
    }

    public static byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] plaintext) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return c.doFinal(plaintext);
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] nonce, byte[] ciphertextWithTag) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return c.doFinal(ciphertextWithTag);
    }

    public static byte[] randomNonce() {
        byte[] n = new byte[NONCE_LEN];
        RNG.nextBytes(n);
        return n;
    }

    public static KeyPair generateSigningKeyPair() throws GeneralSecurityException {
        return generateEphemeralKeyPair();
    }

    public static PrivateKey decodePrivateKeyPkcs8(byte[] pkcs8) throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    public static byte[] encodePrivateKeyPkcs8(PrivateKey privateKey) {
        return privateKey.getEncoded();
    }

    public static byte[] signEcdsaSha256(PrivateKey signingKey, byte[] message) throws GeneralSecurityException {
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(signingKey);
        sig.update(message);
        return sig.sign();
    }

    public static byte[] derToIeee1363(byte[] der) throws GeneralSecurityException {
        if (der == null || der.length < 8 || der[0] != 0x30) {
            throw new GeneralSecurityException("Malformed DER signature");
        }

        int seqLen;
        int idx;
        if ((der[1] & 0x80) == 0) {
            seqLen = der[1] & 0xff;
            idx = 2;
        } else {
            int lenBytes = der[1] & 0x7f;
            if (lenBytes > 2) throw new GeneralSecurityException("DER length too big");
            seqLen = 0;
            for (int i = 0; i < lenBytes; i++) seqLen = (seqLen << 8) | (der[2 + i] & 0xff);
            idx = 2 + lenBytes;
        }
        if (idx + seqLen > der.length) throw new GeneralSecurityException("DER truncated");

        if (der[idx] != 0x02) throw new GeneralSecurityException("Expected INTEGER r");
        int rLen = der[idx + 1] & 0xff;
        int rStart = idx + 2;

        int sHeader = rStart + rLen;
        if (der[sHeader] != 0x02) throw new GeneralSecurityException("Expected INTEGER s");
        int sLen = der[sHeader + 1] & 0xff;
        int sStart = sHeader + 2;

        byte[] r = stripLeadingZero(Arrays.copyOfRange(der, rStart, rStart + rLen));
        byte[] s = stripLeadingZero(Arrays.copyOfRange(der, sStart, sStart + sLen));

        byte[] out = new byte[64];
        System.arraycopy(r, 0, out, 32 - r.length, r.length);
        System.arraycopy(s, 0, out, 64 - s.length, s.length);
        return out;
    }

    private static byte[] stripLeadingZero(byte[] v) {
        int i = 0;
        while (i < v.length - 1 && v[i] == 0x00) i++;
        return Arrays.copyOfRange(v, i, v.length);
    }
}
