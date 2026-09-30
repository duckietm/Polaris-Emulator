package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingDashboardComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HousekeepingGetDashboardEvent extends HousekeepingHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int onlineUsers = Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .size();
        int activeRooms = 0;
        int totalUsers = 0;
        int totalRooms = 0;
        int pendingTickets = 0;
        int sanctionsLast24h = 0;
        int now = Emulator.getIntUnixTimestamp();

        // activeRooms = loaded rooms with at least one user
        try {
            for (var room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                if (room != null && room.getUserCount() > 0) activeRooms++;
            }
        } catch (Exception ignored) {
            // fall through with 0
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users");
                    ResultSet rs = statement.executeQuery()) {
                if (rs.next()) totalUsers = rs.getInt(1);
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM rooms");
                    ResultSet rs = statement.executeQuery()) {
                if (rs.next()) totalRooms = rs.getInt(1);
            }

            try (PreparedStatement statement =
                            connection.prepareStatement("SELECT COUNT(*) FROM support_tickets WHERE state = 0");
                    ResultSet rs = statement.executeQuery()) {
                if (rs.next()) pendingTickets = rs.getInt(1);
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT COUNT(*) FROM bans WHERE timestamp > ?")) {
                statement.setInt(1, now - (24 * 3600));
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) sanctionsLast24h = rs.getInt(1);
                }
            }
        } catch (SQLException ignored) {
            // Surface 0s rather than failing the whole dashboard on a missing
            // optional table — the HK panel can render partial data.
        }

        int uptime = (int) ((System.currentTimeMillis() - HOUSEKEEPING_BOOT_MILLIS) / 1000);
        String version = "Polaris";

        this.client.sendResponse(new HousekeepingDashboardComposer(
                onlineUsers,
                totalUsers,
                activeRooms,
                totalRooms,
                onlineUsers, // peakOnlineToday — not tracked, use current as best-effort
                onlineUsers, // peakOnlineAllTime — same
                pendingTickets,
                sanctionsLast24h,
                Math.max(uptime, 0),
                version));
    }

    // Approximate uptime — captured at class-load time rather than emu startup
    // (Emulator.java doesn't expose a public startup timestamp). For HK panel
    // headline metrics this is close enough; if tighter accuracy is needed
    // later, plumb Emulator.startup through and read it here.
    private static final long HOUSEKEEPING_BOOT_MILLIS = System.currentTimeMillis();
}
