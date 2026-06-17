package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.EMAIL_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.USERNAME_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.checkPassword;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readString;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

final class AccountChangeEndpoints {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountChangeEndpoints.class);

    private AccountChangeEndpoints() {
    }

    static void handleChangePassword(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        int userId = verifyBearer(req, ip, ctx);
        if (userId <= 0) return;

        String currentPassword = readString(body, "currentPassword");
        String newPassword     = readString(body, "newPassword");
        String confirmPassword = readString(body, "confirmPassword");

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("All fields are required."));
            return;
        }

        if (currentPassword.length() > 256 || newPassword.length() > 256) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Password too long."));
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("New passwords do not match."));
            return;
        }

        if (newPassword.length() < 8) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Password must be at least 8 characters."));
            return;
        }

        if (newPassword.equals(currentPassword)) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("New password must be different from the current password."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            String storedHash = null;
            String username = null;
            try (PreparedStatement lookup = conn.prepareStatement(
                    "SELECT username, password FROM users WHERE id = ? LIMIT 1")) {
                lookup.setInt(1, userId);
                try (ResultSet rs = lookup.executeQuery()) {
                    if (rs.next()) {
                        username = rs.getString("username");
                        storedHash = rs.getString("password");
                    }
                }
            }

            if (storedHash == null || storedHash.isEmpty()) {
                AuthRateLimiter.recordFailure(ip);
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("Account not found."));
                return;
            }

            if (!checkPassword(currentPassword, storedHash)) {
                AuthRateLimiter.recordFailure(ip);
                LOGGER.info("[auth/change-password] current password mismatch for user id={} username='{}'", userId, username);
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED,
                        errorPayload("Current password is incorrect."));
                return;
            }

            String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET password = ? WHERE id = ? LIMIT 1")) {
                upd.setString(1, hashed);
                upd.setInt(2, userId);
                upd.executeUpdate();
            }

            AuthRateLimiter.recordSuccess(ip);
            LOGGER.info("[auth/change-password] password updated for user id={} username='{}' ip='{}'", userId, username, ip);

            JsonObject ok = new JsonObject();
            ok.addProperty("message", "Password updated successfully.");
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("[auth/change-password] failed for user id=" + userId, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleChangeEmail(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        int userId = verifyBearer(req, ip, ctx);
        if (userId <= 0) return;

        String currentPassword = readString(body, "currentPassword");
        String newEmail        = readString(body, "newEmail").trim();

        if (currentPassword.isEmpty() || newEmail.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("All fields are required."));
            return;
        }

        if (currentPassword.length() > 256 || newEmail.length() > 254) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Field too long."));
            return;
        }

        if (!EMAIL_RE.matcher(newEmail).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Invalid email address."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            String storedHash = null;
            String username = null;
            String currentEmail = null;
            try (PreparedStatement lookup = conn.prepareStatement(
                    "SELECT username, password, mail FROM users WHERE id = ? LIMIT 1")) {
                lookup.setInt(1, userId);
                try (ResultSet rs = lookup.executeQuery()) {
                    if (rs.next()) {
                        username = rs.getString("username");
                        storedHash = rs.getString("password");
                        currentEmail = rs.getString("mail");
                    }
                }
            }

            if (storedHash == null || storedHash.isEmpty()) {
                AuthRateLimiter.recordFailure(ip);
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("Account not found."));
                return;
            }

            if (!checkPassword(currentPassword, storedHash)) {
                AuthRateLimiter.recordFailure(ip);
                LOGGER.info("[auth/change-email] password mismatch for user id={} username='{}'", userId, username);
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED,
                        errorPayload("Current password is incorrect."));
                return;
            }

            if (currentEmail != null && currentEmail.equalsIgnoreCase(newEmail)) {
                sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                        errorPayload("New email must be different from the current email."));
                return;
            }

            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM users WHERE mail = ? AND id <> ? LIMIT 1")) {
                check.setString(1, newEmail);
                check.setInt(2, userId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        sendJson(ctx, req, HttpResponseStatus.CONFLICT,
                                errorPayload("That email address is already in use."));
                        return;
                    }
                }
            }

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET mail = ? WHERE id = ? LIMIT 1")) {
                upd.setString(1, newEmail);
                upd.setInt(2, userId);
                upd.executeUpdate();
            }

            if (currentEmail != null && !currentEmail.isEmpty()) AvailabilityCache.invalidateEmail(currentEmail);
            AvailabilityCache.invalidateEmail(newEmail);

            AuthRateLimiter.recordSuccess(ip);
            LOGGER.info("[auth/change-email] email updated for user id={} username='{}' ip='{}'", userId, username, ip);

            JsonObject ok = new JsonObject();
            ok.addProperty("message", "Email updated successfully.");
            ok.addProperty("email", newEmail);
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("[auth/change-email] failed for user id=" + userId, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleChangeUsername(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        int userId = verifyBearer(req, ip, ctx);
        if (userId <= 0) return;

        String currentPassword = readString(body, "currentPassword");
        String newUsername     = readString(body, "newUsername").trim();

        if (currentPassword.isEmpty() || newUsername.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("All fields are required."));
            return;
        }

        if (currentPassword.length() > 256) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Field too long."));
            return;
        }

        if (newUsername.length() > 25) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Username can be at most 25 characters."));
            return;
        }

        if (!USERNAME_RE.matcher(newUsername).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Username must be 3-25 characters (letters, numbers, . _ -)."));
            return;
        }

        long cooldownDays = Math.max(0, Emulator.getConfig().getInt("rename.cooldown_days", 30));
        long cooldownSeconds = cooldownDays * 86400L;

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            String storedHash = null;
            String currentUsername = null;
            int lastChange = 0;
            boolean cooldownColumnExists = true;

            try (PreparedStatement lookup = conn.prepareStatement(
                    "SELECT username, password, last_username_change FROM users WHERE id = ? LIMIT 1")) {
                lookup.setInt(1, userId);
                try (ResultSet rs = lookup.executeQuery()) {
                    if (rs.next()) {
                        currentUsername = rs.getString("username");
                        storedHash = rs.getString("password");
                        lastChange = rs.getInt("last_username_change");
                    }
                }
            } catch (SQLException missingColumn) {
                cooldownColumnExists = false;
                LOGGER.warn("[auth/change-username] users.last_username_change column missing — cooldown disabled. Run the migration in config/Database.sql.");
                try (PreparedStatement lookup = conn.prepareStatement(
                        "SELECT username, password FROM users WHERE id = ? LIMIT 1")) {
                    lookup.setInt(1, userId);
                    try (ResultSet rs = lookup.executeQuery()) {
                        if (rs.next()) {
                            currentUsername = rs.getString("username");
                            storedHash = rs.getString("password");
                        }
                    }
                }
            }

            if (storedHash == null || storedHash.isEmpty()) {
                AuthRateLimiter.recordFailure(ip);
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("Account not found."));
                return;
            }

            if (!checkPassword(currentPassword, storedHash)) {
                AuthRateLimiter.recordFailure(ip);
                LOGGER.info("[auth/change-username] password mismatch for user id={} username='{}'", userId, currentUsername);
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED,
                        errorPayload("Current password is incorrect."));
                return;
            }

            if (currentUsername != null && currentUsername.equals(newUsername)) {
                sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                        errorPayload("New username must be different from the current username."));
                return;
            }

            int now = Emulator.getIntUnixTimestamp();
            if (cooldownColumnExists && cooldownSeconds > 0 && lastChange > 0) {
                long allowedAt = (long) lastChange + cooldownSeconds;
                if (now < allowedAt) {
                    long remaining = allowedAt - now;
                    long days = remaining / 86400L;
                    long hours = (remaining % 86400L) / 3600L;
                    String wait = days > 0 ? (days + " day" + (days == 1 ? "" : "s")) : (hours + " hour" + (hours == 1 ? "" : "s"));
                    sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                            errorPayload("You can rename again in " + wait + "."));
                    return;
                }
            }

            try (PreparedStatement banned = conn.prepareStatement(
                    "SELECT 1 FROM banned_usernames WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
                banned.setString(1, newUsername);
                try (ResultSet rs = banned.executeQuery()) {
                    if (rs.next()) {
                        sendJson(ctx, req, HttpResponseStatus.CONFLICT,
                                errorPayload("That username is not allowed."));
                        return;
                    }
                }
            } catch (SQLException bannedTableError) {
                if (bannedTableError.getErrorCode() != 1146
                        && !"42S02".equals(bannedTableError.getSQLState())) {
                    throw bannedTableError;
                }
            }

            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM users WHERE LOWER(username) = LOWER(?) AND id <> ? LIMIT 1")) {
                check.setString(1, newUsername);
                check.setInt(2, userId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        sendJson(ctx, req, HttpResponseStatus.CONFLICT,
                                errorPayload("That username is already taken."));
                        return;
                    }
                }
            }

            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            boolean cooldownRace = false;
            boolean duplicateName = false;

            try {
                int rowsUpdated = 0;

                try (PreparedStatement upd = conn.prepareStatement(
                        cooldownColumnExists
                                ? "UPDATE users SET username = ?, last_username_change = ? "
                                + "WHERE id = ? "
                                + "  AND (last_username_change = 0 OR last_username_change + ? <= ?) "
                                + "LIMIT 1"
                                : "UPDATE users SET username = ? WHERE id = ? LIMIT 1")) {
                    upd.setString(1, newUsername);
                    if (cooldownColumnExists) {
                        upd.setInt(2, now);
                        upd.setInt(3, userId);
                        upd.setLong(4, cooldownSeconds);
                        upd.setInt(5, now);
                    } else {
                        upd.setInt(2, userId);
                    }
                    try {
                        rowsUpdated = upd.executeUpdate();
                    } catch (SQLException dup) {
                        if (dup.getErrorCode() == 1062 || "23000".equals(dup.getSQLState())) {
                            duplicateName = true;
                        } else {
                            throw dup;
                        }
                    }
                }

                if (duplicateName || (cooldownColumnExists && rowsUpdated == 0)) {
                    if (!duplicateName) cooldownRace = true;
                    conn.rollback();
                } else {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE rooms SET owner_name = ? WHERE owner_id = ?")) {
                        upd.setString(1, newUsername);
                        upd.setInt(2, userId);
                        upd.executeUpdate();
                    }

                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE rooms_for_sale SET owner_name = ? WHERE user_id = ?")) {
                        upd.setString(1, newUsername);
                        upd.setInt(2, userId);
                        upd.executeUpdate();
                    } catch (SQLException roomsForSale) {
                        if (roomsForSale.getErrorCode() != 1146
                                && !"42S02".equals(roomsForSale.getSQLState())) {
                            throw roomsForSale;
                        }
                    }

                    conn.commit();
                }
            } catch (SQLException txError) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw txError;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }

            if (duplicateName) {
                LOGGER.info("[auth/change-username] dup-entry race for user id={} wanted='{}'", userId, newUsername);
                sendJson(ctx, req, HttpResponseStatus.CONFLICT,
                        errorPayload("That username is already taken."));
                return;
            }

            if (cooldownRace) {
                LOGGER.info("[auth/change-username] cooldown race for user id={} (concurrent rename rejected)", userId);
                sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                        errorPayload("Rename already in progress — please wait."));
                return;
            }

            try {
                if (Emulator.getGameServer() != null && Emulator.getGameServer().getGameClientManager() != null
                        && Emulator.getGameEnvironment() != null && Emulator.getGameEnvironment().getHabboManager() != null) {
                    com.eu.habbo.habbohotel.users.Habbo habbo =
                            Emulator.getGameServer().getGameClientManager().getHabbo(userId);
                    if (habbo != null) {
                        Emulator.getGameEnvironment().getHabboManager().removeHabbo(habbo);
                        habbo.getHabboInfo().setUsername(newUsername);
                        Emulator.getGameEnvironment().getHabboManager().addHabbo(habbo);
                    }
                }
            } catch (Exception cacheError) {
                LOGGER.warn("[auth/change-username] failed to refresh HabboManager cache", cacheError);
            }

            try {
                if (Emulator.getGameEnvironment() != null && Emulator.getGameEnvironment().getRoomManager() != null) {
                    for (com.eu.habbo.habbohotel.rooms.Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                        if (room.getOwnerId() == userId) {
                            room.setOwnerName(newUsername);
                        }
                    }
                }
            } catch (Exception cacheError) {
                LOGGER.warn("[auth/change-username] failed to refresh Room.ownerName cache", cacheError);
            }

            try {
                com.eu.habbo.messages.incoming.catalog.marketplace.RequestOffersEvent.cachedResults.clear();
            } catch (Exception cacheError) {
                LOGGER.warn("[auth/change-username] failed to clear marketplace cache", cacheError);
            }

            try (PreparedStatement clear = conn.prepareStatement(
                    "UPDATE users SET auth_ticket = '', online = '0' WHERE id = ? LIMIT 1")) {
                clear.setInt(1, userId);
                clear.executeUpdate();
            }

            if (Emulator.getGameServer() != null
                    && Emulator.getGameServer().getGameClientManager() != null) {
                com.eu.habbo.habbohotel.users.Habbo habbo =
                        Emulator.getGameServer().getGameClientManager().getHabbo(userId);
                if (habbo != null && habbo.getClient() != null) {
                    Emulator.getGameServer().getGameClientManager().forceDisposeClient(habbo.getClient());
                }
            }

            AuthRateLimiter.recordSuccess(ip);
            LOGGER.info("[auth/change-username] '{}' -> '{}' (user id={}, ip='{}')",
                    currentUsername, newUsername, userId, ip);

            JsonObject ok = new JsonObject();
            ok.addProperty("message", "Username updated. Please log in again with your new name.");
            ok.addProperty("username", newUsername);
            ok.addProperty("relogin", true);
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("[auth/change-username] failed for user id=" + userId, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    private static int verifyBearer(FullHttpRequest req, String ip, ChannelHandlerContext ctx) {
        String authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String bearer = "";
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            bearer = authHeader.substring(7).trim();
        }

        int userId = AccessTokenService.verify(bearer);
        if (userId <= 0) {
            AuthRateLimiter.recordFailure(ip);
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("Not authenticated."));
        }
        return userId;
    }
}
