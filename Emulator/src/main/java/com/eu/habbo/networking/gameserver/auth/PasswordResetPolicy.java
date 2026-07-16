package com.eu.habbo.networking.gameserver.auth;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class PasswordResetPolicy {
    private static final String HASH_PREFIX = "sha256:";

    private PasswordResetPolicy() {
    }

    static String storedToken(String rawToken, boolean secureStorage) {
        return secureStorage ? tokenDigest(rawToken) : rawToken;
    }

    static String tokenDigest(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HASH_PREFIX + HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static boolean isSecureOrLoopbackUrl(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            return InetAddress.getByName(uri.getHost()).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
