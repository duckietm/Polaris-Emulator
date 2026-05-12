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

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.EMAIL_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.USERNAME_RE;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readString;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

final class AccountCheckEndpoints {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountCheckEndpoints.class);

    private AccountCheckEndpoints() {
    }

    static void handleCheckEmail(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        if (!AuthRateLimiter.tryProbe(ip)) {
            long secs = AuthRateLimiter.secondsUntilProbeReset(ip);
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                    errorPayload("Too many requests. Try again in " + secs + "s."));
            return;
        }
        String email = readString(body, "email").trim();
        if (email.isEmpty() || email.length() > 254 || !EMAIL_RE.matcher(email).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Invalid email address."));
            return;
        }

        Boolean cached = AvailabilityCache.lookupEmail(email);
        boolean taken;
        if (cached != null) {
            taken = !cached;
        } else {
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT 1 FROM users WHERE mail = ? LIMIT 1")) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    taken = rs.next();
                }
            } catch (Exception e) {
                LOGGER.error("check-email failed", e);
                sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
                return;
            }
            AvailabilityCache.storeEmail(email, !taken);
        }

        JsonObject res = new JsonObject();
        res.addProperty("available", !taken);
        if (taken) res.addProperty("error", "This email is already in use.");
        sendJson(ctx, req, HttpResponseStatus.OK, res);
    }

    static void handleCheckUsername(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body, String ip) {
        if (!AuthRateLimiter.tryProbe(ip)) {
            long secs = AuthRateLimiter.secondsUntilProbeReset(ip);
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                    errorPayload("Too many requests. Try again in " + secs + "s."));
            return;
        }
        String username = readString(body, "username").trim();
        if (!USERNAME_RE.matcher(username).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST,
                    errorPayload("Username must be 3-32 chars (letters, numbers, . _ -)."));
            return;
        }

        Boolean cached = AvailabilityCache.lookupUsername(username);
        boolean taken;
        if (cached != null) {
            taken = !cached;
        } else {
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT 1 FROM users WHERE username = ? LIMIT 1")) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    taken = rs.next();
                }
            } catch (Exception e) {
                LOGGER.error("check-username failed", e);
                sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
                return;
            }
            AvailabilityCache.storeUsername(username, !taken);
        }

        JsonObject res = new JsonObject();
        res.addProperty("available", !taken);
        if (taken) res.addProperty("error", "This Habbo name is already taken.");
        sendJson(ctx, req, HttpResponseStatus.OK, res);
    }
}
