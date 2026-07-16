package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

public final class SsoTicketPolicy {
    public static final int DEFAULT_TTL_SECONDS = 60;

    private SsoTicketPolicy() {
    }

    public static TicketSession register(Connection connection, int userId, String ticket) throws SQLException {
        if (connection == null || userId <= 0 || ticket == null || ticket.isEmpty()) {
            throw new IllegalArgumentException("valid connection, user and ticket are required");
        }

        String ticketHash = hash(ticket);
        Timestamp expiry = newExpiry();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO users_auth_ticket_sessions (ticket_hash, user_id, expires_at) VALUES (?, ?, ?)")) {
            insert.setString(1, ticketHash);
            insert.setInt(2, userId);
            insert.setTimestamp(3, expiry);
            insert.executeUpdate();
        }

        try (PreparedStatement select = connection.prepareStatement(
                "SELECT expires_at FROM users_auth_ticket_sessions "
                        + "WHERE ticket_hash = ? AND user_id = ? AND expires_at >= NOW() LIMIT 1")) {
            select.setString(1, ticketHash);
            select.setInt(2, userId);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new TicketSession(userId, result.getTimestamp("expires_at"));
            }
        }
    }

    /**
     * Resolves both built-in and external-CMS tickets. A previously unseen
     * ticket is enrolled on first use without requiring the CMS to write TTL
     * metadata; subsequent uses retain the original deadline and cannot extend it.
     */
    public static TicketSession resolve(Connection connection, String ticket) throws SQLException {
        if (connection == null || ticket == null || ticket.isEmpty() || ticket.length() > 128) {
            return null;
        }

        int userId = 0;
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT id FROM users WHERE auth_ticket = ? LIMIT 1")) {
            lookup.setString(1, ticket);
            try (ResultSet result = lookup.executeQuery()) {
                if (result.next()) {
                    userId = result.getInt("id");
                }
            }
        }

        return userId > 0 ? register(connection, userId, ticket) : null;
    }

    public static void revoke(Connection connection, String ticket) throws SQLException {
        if (connection == null || ticket == null || ticket.isEmpty()) {
            return;
        }

        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM users_auth_ticket_sessions WHERE ticket_hash = ?")) {
            delete.setString(1, hash(ticket));
            delete.executeUpdate();
        }
    }

    private static Timestamp newExpiry() {
        int ttlSeconds = Math.max(1,
                Emulator.getConfig().getInt("login.sso.ticket.ttl.seconds", DEFAULT_TTL_SECONDS));
        return Timestamp.from(Instant.now().plusSeconds(ttlSeconds));
    }

    private static String hash(String ticket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(ticket.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record TicketSession(int userId, Timestamp expiresAt) {
    }
}
