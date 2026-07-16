package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readString;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

final class PasswordResetEndpoints {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetEndpoints.class);

    private PasswordResetEndpoints() {
    }

    static void handleReset(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        String token = readString(body, "token").trim();
        String password = readString(body, "password");
        String confirm = readString(body, "passwordConfirm");

        if (token.isEmpty() || token.length() > 128) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Invalid reset token."));
            return;
        }
        if (password.length() < 8 || password.length() > 128) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Password must be between 8 and 128 characters."));
            return;
        }
        if (!confirm.isEmpty() && !password.equals(confirm)) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Passwords do not match."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int userId = 0;
                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT user_id FROM password_resets "
                                + "WHERE token IN (?, ?) AND expires_at >= NOW() LIMIT 1 FOR UPDATE")) {
                    find.setString(1, token);
                    find.setString(2, PasswordResetPolicy.tokenDigest(token));
                    try (ResultSet result = find.executeQuery()) {
                        if (result.next()) userId = result.getInt("user_id");
                    }
                }

                if (userId <= 0) {
                    conn.rollback();
                    AuthRateLimiter.recordFailure(ip);
                    sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED,
                            errorPayload("Reset token is invalid or expired."));
                    return;
                }

                String passwordHash = PasswordHasher.hash(password, 12);
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE users SET password = ?, auth_ticket = '' WHERE id = ? LIMIT 1")) {
                    update.setString(1, passwordHash);
                    update.setInt(2, userId);
                    if (update.executeUpdate() != 1) throw new IllegalStateException("Reset user disappeared");
                }
                try (PreparedStatement revoke = conn.prepareStatement(
                        "UPDATE users_remember_families SET revoked = 1 WHERE user_id = ?")) {
                    revoke.setInt(1, userId);
                    revoke.executeUpdate();
                }
                try (PreparedStatement consume = conn.prepareStatement(
                        "DELETE FROM password_resets WHERE user_id = ?")) {
                    consume.setInt(1, userId);
                    consume.executeUpdate();
                }

                conn.commit();
                AuthRateLimiter.recordSuccess(ip);
                JsonObject ok = new JsonObject();
                ok.addProperty("message", "Password reset complete.");
                sendJson(ctx, req, HttpResponseStatus.OK, ok);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            LOGGER.error("Password reset failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }
}
