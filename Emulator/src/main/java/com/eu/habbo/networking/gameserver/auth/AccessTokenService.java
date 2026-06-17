package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;

public final class AccessTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder URL_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DEC = Base64.getUrlDecoder();
    private static final int MAX_TOKEN_CHARS = 2048;

    private static volatile String cachedSecret = null;

    private AccessTokenService() {}

    public static final class Issued {
        public final String token;
        public final long expiresAt;

        Issued(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    public static long ttlSeconds() {
        return Math.max(60L, Emulator.getConfig().getInt("login.access.jwt.ttl.seconds", 86400));
    }

    public static Issued issue(int userId) {
        long now = Emulator.getIntUnixTimestamp();
        long exp = now + ttlSeconds();

        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject payload = new JsonObject();
        payload.addProperty("sub", userId);
        payload.addProperty("iat", now);
        payload.addProperty("exp", exp);
        payload.addProperty("typ", "access");

        String h = URL_ENC.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = URL_ENC.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = h + "." + p;
        String sig = URL_ENC.encodeToString(hmacSha256(secret().getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8)));
        return new Issued(signingInput + "." + sig, exp);
    }

    public static int verify(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_CHARS) return 0;

        String[] parts = token.split("\\.");
        if (parts.length != 3) return 0;

        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expected = hmacSha256(secret().getBytes(StandardCharsets.UTF_8),
                    signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] provided = URL_DEC.decode(parts[2]);
            if (!constantTimeEquals(expected, provided)) return 0;

            byte[] payloadBytes = URL_DEC.decode(parts[1]);
            JsonObject payload = JsonParser.parseString(new String(payloadBytes, StandardCharsets.UTF_8)).getAsJsonObject();

            if (!payload.has("typ") || !"access".equals(payload.get("typ").getAsString())) return 0;
            long exp = payload.get("exp").getAsLong();
            if (exp <= Emulator.getIntUnixTimestamp()) return 0;
            return payload.get("sub").getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String secret() {
        String s = cachedSecret;
        if (s != null && !s.isEmpty()) return s;

        synchronized (AccessTokenService.class) {
            if (cachedSecret != null && !cachedSecret.isEmpty()) return cachedSecret;

            String configured = Emulator.getConfig().getValue("login.access.jwt.secret", "");
            if (configured != null && !configured.isEmpty()) {
                cachedSecret = configured;
                return configured;
            }

            byte[] buf = new byte[48];
            RNG.nextBytes(buf);
            String generated = Base64.getEncoder().withoutPadding().encodeToString(buf);

            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO emulator_settings (`key`, `value`) VALUES ('login.access.jwt.secret', ?) "
                                 + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)")) {
                stmt.setString(1, generated);
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Could not persist generated login.access.jwt.secret; using in-memory only", e);
            }

            Emulator.getConfig().update("login.access.jwt.secret", generated);
            cachedSecret = generated;
            LOGGER.info("[auth/access] generated new access token signing secret (persisted to emulator_settings)");
            return generated;
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }
}
