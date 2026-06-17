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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.UUID;

public final class RememberJwtService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RememberJwtService.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder URL_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DEC = Base64.getUrlDecoder();
    private static final int MAX_TOKEN_CHARS = 2048;

    private static volatile String cachedSecret = null;

    private RememberJwtService() {}

    public static final class RotationResult {
        public final String jwt;
        public final int userId;
        public final String username;
        public final long expiresAt;

        RotationResult(String jwt, int userId, String username, long expiresAt) {
            this.jwt = jwt;
            this.userId = userId;
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    private static int familyTtlDays() {
        int configured = Emulator.getConfig().getInt("login.remember.duration.days", 0);
        if (configured <= 0) configured = Emulator.getConfig().getInt("login.remember.days", 30);
        return Math.max(1, configured);
    }

    private static long familyTtlSeconds() {
        return familyTtlDays() * 86400L;
    }

    private static String secret() {
        String s = cachedSecret;
        if (s != null && !s.isEmpty()) return s;

        synchronized (RememberJwtService.class) {
            if (cachedSecret != null && !cachedSecret.isEmpty()) return cachedSecret;

            String configured = Emulator.getConfig().getValue("login.remember.jwt.secret", "");
            if (configured != null && !configured.isEmpty()) {
                cachedSecret = configured;
                return configured;
            }

            byte[] buf = new byte[48];
            RNG.nextBytes(buf);
            String generated = Base64.getEncoder().withoutPadding().encodeToString(buf);

            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO emulator_settings (`key`, `value`) VALUES ('login.remember.jwt.secret', ?) "
                                 + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)")) {
                stmt.setString(1, generated);
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Could not persist generated login.remember.jwt.secret; using in-memory only", e);
            }

            Emulator.getConfig().update("login.remember.jwt.secret", generated);
            cachedSecret = generated;
            LOGGER.info("[auth/remember] generated new JWT signing secret (persisted to emulator_settings)");
            return generated;
        }
    }

    public static RotationResult issueForNewFamily(Connection conn, int userId, String username, String ip) throws SQLException {
        String familyId = UUID.randomUUID().toString();
        long now = Emulator.getIntUnixTimestamp();
        long expiresAt = now + familyTtlSeconds();

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO users_remember_families (family_id, user_id, current_version, created_at, expires_at, revoked, last_ip) "
                        + "VALUES (?, ?, 1, ?, ?, 0, ?)")) {
            ins.setString(1, familyId);
            ins.setInt(2, userId);
            ins.setLong(3, now);
            ins.setLong(4, expiresAt);
            ins.setString(5, ip == null ? "" : ip);
            ins.executeUpdate();
        }

        String jwt = buildJwt(userId, familyId, 1, now, expiresAt);
        return new RotationResult(jwt, userId, username, expiresAt);
    }

    public static RotationResult rotate(Connection conn, String jwt, String ip) {
        ParsedJwt parsed;
        try {
            parsed = verifyAndParse(jwt);
        } catch (Exception e) {
            LOGGER.debug("[auth/remember] invalid JWT: {}", e.getMessage());
            return null;
        }

        long now = Emulator.getIntUnixTimestamp();
        if (parsed.exp <= now) return null;

        int familyVersion = 0;
        boolean revoked = false;
        long familyExpiresAt = 0;
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT current_version, revoked, expires_at FROM users_remember_families WHERE family_id = ? AND user_id = ? LIMIT 1")) {
            sel.setString(1, parsed.familyId);
            sel.setInt(2, parsed.userId);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) return null;
                familyVersion = rs.getInt("current_version");
                revoked = rs.getInt("revoked") != 0;
                familyExpiresAt = rs.getLong("expires_at");
            }
        } catch (SQLException e) {
            LOGGER.error("[auth/remember] family lookup failed", e);
            return null;
        }

        if (revoked || familyExpiresAt <= now) return null;

        if (parsed.version < familyVersion) {
            LOGGER.warn("[auth/remember] replay detected: familyId={} presented v={} but current is v={}, revoking family",
                    parsed.familyId, parsed.version, familyVersion);
            revokeFamilyById(conn, parsed.familyId);
            return null;
        }
        if (parsed.version > familyVersion) {
            LOGGER.warn("[auth/remember] future version: familyId={} presented v={} but current is v={}",
                    parsed.familyId, parsed.version, familyVersion);
            return null;
        }

        int newVersion = familyVersion + 1;
        long newExpiresAt = now + familyTtlSeconds();

        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE users_remember_families SET current_version = ?, expires_at = ?, last_ip = ? "
                        + "WHERE family_id = ? AND current_version = ? AND revoked = 0")) {
            upd.setInt(1, newVersion);
            upd.setLong(2, newExpiresAt);
            upd.setString(3, ip == null ? "" : ip);
            upd.setString(4, parsed.familyId);
            upd.setInt(5, familyVersion);
            int rows = upd.executeUpdate();
            if (rows == 0) return null;
        } catch (SQLException e) {
            LOGGER.error("[auth/remember] rotation update failed", e);
            return null;
        }

        String username = null;
        try (PreparedStatement usr = conn.prepareStatement("SELECT username FROM users WHERE id = ? LIMIT 1")) {
            usr.setInt(1, parsed.userId);
            try (ResultSet rs = usr.executeQuery()) {
                if (rs.next()) username = rs.getString("username");
            }
        } catch (SQLException e) {
            LOGGER.error("[auth/remember] username lookup failed", e);
        }

        if (username == null) return null;

        String newJwt = buildJwt(parsed.userId, parsed.familyId, newVersion, now, newExpiresAt);
        return new RotationResult(newJwt, parsed.userId, username, newExpiresAt);
    }

    public static void revokeFromToken(Connection conn, String jwt) {
        try {
            ParsedJwt p = verifyAndParse(jwt);
            revokeFamilyById(conn, p.familyId);
        } catch (Exception ignored) { }
    }

    private static void revokeFamilyById(Connection conn, String familyId) {
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE users_remember_families SET revoked = 1 WHERE family_id = ?")) {
            upd.setString(1, familyId);
            upd.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[auth/remember] revoke failed for familyId=" + familyId, e);
        }
    }

    private static String buildJwt(int userId, String familyId, int version, long iat, long exp) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject payload = new JsonObject();
        payload.addProperty("sub", userId);
        payload.addProperty("fid", familyId);
        payload.addProperty("v",   version);
        payload.addProperty("iat", iat);
        payload.addProperty("exp", exp);
        payload.addProperty("typ", "refresh");

        String h = URL_ENC.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = URL_ENC.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = h + "." + p;
        String sig = URL_ENC.encodeToString(hmacSha256(secret().getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + sig;
    }

    private static final class ParsedJwt {
        final int userId;
        final String familyId;
        final int version;
        final long exp;

        ParsedJwt(int userId, String familyId, int version, long exp) {
            this.userId = userId;
            this.familyId = familyId;
            this.version = version;
            this.exp = exp;
        }
    }

    private static ParsedJwt verifyAndParse(String jwt) throws Exception {
        if (jwt == null || jwt.isEmpty() || jwt.length() > MAX_TOKEN_CHARS) throw new IllegalArgumentException("empty");

        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("not 3 segments");

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmacSha256(secret().getBytes(StandardCharsets.UTF_8), signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] provided = URL_DEC.decode(parts[2]);
        if (!constantTimeEquals(expected, provided)) throw new SecurityException("bad signature");

        byte[] payloadBytes = URL_DEC.decode(parts[1]);
        JsonObject payload = JsonParser.parseString(new String(payloadBytes, StandardCharsets.UTF_8)).getAsJsonObject();

        if (!payload.has("typ") || !"refresh".equals(payload.get("typ").getAsString())) throw new IllegalArgumentException("wrong typ");
        int userId  = payload.get("sub").getAsInt();
        String fid  = payload.get("fid").getAsString();
        int version = payload.get("v").getAsInt();
        long exp    = payload.get("exp").getAsLong();

        return new ParsedJwt(userId, fid, version, exp);
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
