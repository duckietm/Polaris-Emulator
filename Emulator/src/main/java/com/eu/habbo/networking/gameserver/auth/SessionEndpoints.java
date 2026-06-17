package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.EMAIL_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.FIGURE_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.USERNAME_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.bannedPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.checkPassword;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.lookupAccountBan;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.lookupIpBan;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.mintResetToken;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.mintSsoTicket;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readBoolean;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readInt;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readString;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

final class SessionEndpoints {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionEndpoints.class);

    private SessionEndpoints() {
    }

    static void handleLogout(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body) {
        String ssoTicket = readString(body, "ssoTicket");
        String rememberToken = readString(body, "rememberToken").trim();
        JsonObject ok = new JsonObject();
        ok.addProperty("message", "Logged out.");

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            int userId = 0;

            if (ssoTicket != null && !ssoTicket.isEmpty()) {
                try (PreparedStatement lookup = conn.prepareStatement(
                        "SELECT id FROM users WHERE auth_ticket = ? AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()) LIMIT 1")) {
                    lookup.setString(1, ssoTicket);
                    try (ResultSet rs = lookup.executeQuery()) {
                        if (rs.next()) userId = rs.getInt("id");
                    }
                }

                if (userId > 0) {
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
                }
            }

            if (!rememberToken.isEmpty()) {
                RememberJwtService.revokeFromToken(conn, rememberToken);
            }
        } catch (Exception e) {
            LOGGER.error("Logout cleanup failed", e);
        }

        sendJson(ctx, req, HttpResponseStatus.OK, ok);
    }

    static void handleRemember(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        String jwt = readString(body, "rememberToken").trim();
        if (jwt.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Missing rememberToken."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            RememberJwtService.RotationResult rot = RememberJwtService.rotate(conn, jwt, ip);
            if (rot == null) {
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("Remember token invalid or expired."));
                return;
            }

            String ssoTicket = mintSsoTicket();
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET auth_ticket = ?, ip_current = ? WHERE id = ? LIMIT 1")) {
                upd.setString(1, ssoTicket);
                upd.setString(2, ip == null ? "" : ip);
                upd.setInt(3, rot.userId);
                upd.executeUpdate();
            }

            JsonObject ok = new JsonObject();
            ok.addProperty("ssoTicket", ssoTicket);
            ok.addProperty("username", rot.username);
            ok.addProperty("rememberToken", rot.jwt);
            ok.addProperty("expiresAt", rot.expiresAt);
            ok.addProperty("rememberExpiresAt", rot.expiresAt);
            AccessTokenService.Issued access = AccessTokenService.issue(rot.userId);
            ok.addProperty("accessToken", access.token);
            ok.addProperty("accessTokenExpiresAt", access.expiresAt);
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("Remember login failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleSsoToken(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        String ssoTicket = readString(body, "ssoTicket").trim();
        if (ssoTicket.isEmpty() || ssoTicket.length() > 128) {
            AuthRateLimiter.recordFailure(ip);
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Missing or invalid ssoTicket."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement lookup = conn.prepareStatement(
                     "SELECT id, username FROM users WHERE auth_ticket = ? AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()) LIMIT 1")) {
            lookup.setString(1, ssoTicket);
            try (ResultSet rs = lookup.executeQuery()) {
                if (!rs.next()) {
                    AuthRateLimiter.recordFailure(ip);
                    sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("SSO ticket not recognised."));
                    return;
                }
                int userId = rs.getInt("id");
                String username = rs.getString("username");

                AuthRateLimiter.recordSuccess(ip);

                AccessTokenService.Issued access = AccessTokenService.issue(userId);
                JsonObject ok = new JsonObject();
                ok.addProperty("username", username);
                ok.addProperty("accessToken", access.token);
                ok.addProperty("accessTokenExpiresAt", access.expiresAt);
                sendJson(ctx, req, HttpResponseStatus.OK, ok);
            }
        } catch (Exception e) {
            LOGGER.error("[auth/sso-token] lookup failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleRefresh(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        String jwt = readString(body, "rememberToken").trim();
        if (jwt.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Missing rememberToken."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            RememberJwtService.RotationResult rot = RememberJwtService.rotate(conn, jwt, ip);
            if (rot == null) {
                sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, errorPayload("Remember token invalid or expired."));
                return;
            }
            JsonObject ok = new JsonObject();
            ok.addProperty("rememberToken", rot.jwt);
            ok.addProperty("expiresAt", rot.expiresAt);
            ok.addProperty("rememberExpiresAt", rot.expiresAt);
            AccessTokenService.Issued access = AccessTokenService.issue(rot.userId);
            ok.addProperty("accessToken", access.token);
            ok.addProperty("accessTokenExpiresAt", access.expiresAt);
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("Refresh failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleLogin(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        String username = readString(body, "username").trim();
        String password = readString(body, "password");
        boolean rememberMe = readBoolean(body, "remember", false) || readBoolean(body, "rememberMe", false);

        if (username.isEmpty() || password.isEmpty()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Missing credentials."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            if (ip != null && !ip.isEmpty()) {
                AuthHttpUtil.BanInfo ipBan = lookupIpBan(conn, ip);
                if (ipBan != null) {
                    LOGGER.info("[auth/login] ip ban hit ip={} type={} expires={}",
                            ip, ipBan.type, ipBan.expiresAt);
                    sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, bannedPayload(ipBan));
                    return;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, username, password FROM users WHERE username = ? LIMIT 1")) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        LOGGER.info("[auth/login] user not found username='{}' ip={}", username, ip);
                        AuthRateLimiter.recordFailure(ip);
                        sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED,
                                errorPayload("Invalid Habbo name or password."));
                        return;
                    }

                    int userId = rs.getInt("id");
                    String stored = rs.getString("password");
                    String storedPreview = stored == null
                            ? "<null>"
                            : (stored.isEmpty() ? "<empty>" : stored.substring(0, Math.min(7, stored.length())) + "…(" + stored.length() + " chars)");

                    if (stored == null || stored.isEmpty() || !checkPassword(password, stored)) {
                        LOGGER.info("[auth/login] password mismatch for user id={} username='{}' stored='{}'",
                                userId, username, storedPreview);
                        AuthRateLimiter.recordFailure(ip);
                        sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED,
                                errorPayload("Invalid Habbo name or password."));
                        return;
                    }

                    AuthHttpUtil.BanInfo accountBan = lookupAccountBan(conn, userId);
                    if (accountBan != null) {
                        LOGGER.info("[auth/login] account ban hit userId={} type={} expires={}",
                                userId, accountBan.type, accountBan.expiresAt);
                        AuthRateLimiter.recordSuccess(ip);
                        sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, bannedPayload(accountBan));
                        return;
                    }

                    String ssoTicket = mintSsoTicket();

                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE users SET auth_ticket = ?, ip_current = ? WHERE id = ? LIMIT 1")) {
                        upd.setString(1, ssoTicket);
                        upd.setString(2, ip == null ? "" : ip);
                        upd.setInt(3, userId);
                        upd.executeUpdate();
                    }

                    String rememberToken = null;
                    if (rememberMe) {
                        try {
                            RememberJwtService.RotationResult issued = RememberJwtService.issueForNewFamily(
                                    conn, userId, rs.getString("username"), ip);
                            rememberToken = issued.jwt;
                        } catch (SQLException e) {
                            LOGGER.error("Failed to issue remember-me JWT for userId=" + userId, e);
                        }
                    }

                    AuthRateLimiter.recordSuccess(ip);

                    JsonObject ok = new JsonObject();
                    ok.addProperty("ssoTicket", ssoTicket);
                    ok.addProperty("username", rs.getString("username"));
                    if (rememberToken != null) ok.addProperty("rememberToken", rememberToken);
                    AccessTokenService.Issued access = AccessTokenService.issue(userId);
                    ok.addProperty("accessToken", access.token);
                    ok.addProperty("accessTokenExpiresAt", access.expiresAt);
                    sendJson(ctx, req, HttpResponseStatus.OK, ok);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Login query failed for username=" + username, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleRegister(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        if (!Emulator.getConfig().getBoolean("login.register.enabled", true)) {
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, errorPayload("Registration is closed."));
            return;
        }

        String username = readString(body, "username").trim();
        String email    = readString(body, "email").trim();
        String password = readString(body, "password");
        String figure   = readString(body, "figure").trim();
        String gender   = readString(body, "gender").trim().toUpperCase();
        int templateId  = readInt(body, "templateId", 0);

        if (!USERNAME_RE.matcher(username).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Username must be 3-32 chars (letters, numbers, . _ -)."));
            return;
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Invalid email address."));
            return;
        }
        if (password.length() < 8) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Password must be at least 8 characters."));
            return;
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            int maxPerIp = Emulator.getConfig().getInt("register.max_per_ip", 5);
            if (maxPerIp > 0 && ip != null && !ip.isEmpty()) {
                try (PreparedStatement quota = conn.prepareStatement(
                        "SELECT COUNT(*) FROM users WHERE ip_register = ?")) {
                    quota.setString(1, ip);
                    try (ResultSet rs = quota.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= maxPerIp) {
                            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                                    errorPayload("This IP has reached the maximum of "
                                            + maxPerIp + " registered accounts."));
                            return;
                        }
                    }
                }
            }

            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT username, mail FROM users WHERE username = ? OR mail = ? LIMIT 1")) {
                check.setString(1, username);
                check.setString(2, email);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        String existingUser = rs.getString("username");
                        String existingMail = rs.getString("mail");
                        boolean userTaken = existingUser != null && existingUser.equalsIgnoreCase(username);
                        boolean mailTaken = existingMail != null && existingMail.equalsIgnoreCase(email);
                        String message;
                        if (userTaken && mailTaken)      message = "That Habbo name and email are already in use.";
                        else if (userTaken)              message = "That Habbo name is already in use.";
                        else                             message = "That email address is already in use.";
                        sendJson(ctx, req, HttpResponseStatus.CONFLICT, errorPayload(message));
                        return;
                    }
                }
            }

            String hashed = BCrypt.hashpw(password, BCrypt.gensalt(12));
            String defaultLook = Emulator.getConfig().getValue("register.default.look",
                    "hr-100-7.hd-180-1.ch-210-66.lg-270-82.sh-290-80");
            String defaultMotto = Emulator.getConfig().getValue("register.default.motto", "I love Habbo!");
            int now = Emulator.getIntUnixTimestamp();

            String finalLook = (figure.isEmpty() || !FIGURE_RE.matcher(figure).matches()) ? defaultLook : figure;
            String finalGender = (gender.equals("M") || gender.equals("F")) ? gender : "M";

            int startingCredits  = Math.max(0, Emulator.getConfig().getInt("new_user_credits", 0));
            int startingDuckets  = Math.max(0, Emulator.getConfig().getInt("new_user_duckets", 0));
            int startingDiamonds = Math.max(0, Emulator.getConfig().getInt("new_user_diamonds", 0));

            int newUserId = 0;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users (username, password, mail, account_created, " +
                            "ip_register, ip_current, last_online, last_login, motto, look, gender, " +
                            "credits, `rank`, home_room, machine_id, auth_ticket, online) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, '', '', '0')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, username);
                ins.setString(2, hashed);
                ins.setString(3, email);
                ins.setInt(4, now);
                ins.setString(5, ip == null ? "" : ip);
                ins.setString(6, ip == null ? "" : ip);
                ins.setInt(7, now);
                ins.setInt(8, now);
                ins.setString(9, defaultMotto);
                ins.setString(10, finalLook);
                ins.setString(11, finalGender);
                ins.setInt(12, startingCredits);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    if (keys.next()) newUserId = keys.getInt(1);
                }
            }

            if (newUserId > 0 && (startingDuckets > 0 || startingDiamonds > 0)) {
                RegistrationSupport.seedUserCurrencies(conn, newUserId, startingDuckets, startingDiamonds);
            }

            LOGGER.info("[auth/register] user created id={} username='{}' templateId={} credits={} duckets={} diamonds={}",
                    newUserId, username, templateId, startingCredits, startingDuckets, startingDiamonds);

            if (newUserId > 0 && templateId > 0) {
                RegistrationSupport.cloneTemplateForUser(conn, templateId, newUserId, username);
            } else if (templateId > 0) {
                LOGGER.warn("[auth/register] skipping template clone: user insert did not return an id (username='{}')", username);
            }

            AvailabilityCache.invalidateEmail(email);
            AvailabilityCache.invalidateUsername(username);

            JsonObject ok = new JsonObject();
            ok.addProperty("message", "Welcome aboard, " + username + "! Your account is ready — log in below with the password you just chose.");
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("Register query failed for username=" + username, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleForgot(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        String email = readString(body, "email").trim();

        if (!EMAIL_RE.matcher(email).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Invalid email address."));
            return;
        }

        JsonObject ok = new JsonObject();
        ok.addProperty("message", "Email sent! If an account matches that address you'll find a reset link in your inbox shortly (check spam if it doesn't show up within a minute).");

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, username FROM users WHERE mail = ? LIMIT 1")) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("id");
                    String username = rs.getString("username");
                    String token = mintResetToken();
                    long expiresAt = Instant.now().getEpochSecond() + 60L * 60L; // 1h

                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO password_resets (user_id, token, expires_at, created_ip) " +
                                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                                    "token = VALUES(token), expires_at = VALUES(expires_at), created_ip = VALUES(created_ip)")) {
                        ins.setInt(1, userId);
                        ins.setString(2, token);
                        ins.setTimestamp(3, Timestamp.from(Instant.ofEpochSecond(expiresAt)));
                        ins.setString(4, ip == null ? "" : ip);
                        ins.executeUpdate();
                    }

                    String resetUrlBase = Emulator.getConfig().getValue("password.reset.url",
                            "http://localhost/reset-password");
                    String fullUrl = resetUrlBase + (resetUrlBase.contains("?") ? "&" : "?") + "token=" + token;
                    String subject = "Reset your Habbo password";
                    String message = "Hi " + username + ",\n\n" +
                            "Someone (hopefully you) requested a password reset for your Habbo account.\n" +
                            "Click the link below within the next hour to choose a new password:\n\n" +
                            fullUrl + "\n\n" +
                            "If you didn't request this you can safely ignore this email.";

                    Emulator.getThreading().getService().submit((Runnable) () -> SmtpMailService.send(email, subject, message));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Forgot-password query failed for email=" + email, e);
        }

        sendJson(ctx, req, HttpResponseStatus.OK, ok);
    }
}
