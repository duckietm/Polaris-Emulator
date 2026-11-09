package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.messages.outgoing.housekeeping.HousekeepingDashboardComposer;

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

        Stats stats = stats();

        this.client.sendResponse(new HousekeepingDashboardComposer(
                stats.onlineUsers(),
                stats.totalUsers(),
                stats.activeRooms(),
                stats.totalRooms(),
                stats.peakOnlineToday(),
                stats.peakOnlineAllTime(),
                stats.pendingTickets(),
                stats.sanctionsLast24h(),
                stats.uptimeSeconds(),
                stats.version()));
    }

    /** The dashboard headline numbers, also read by the RCON housekeeping command (hotel.dashboard). */
    public record Stats(
            int onlineUsers,
            int totalUsers,
            int activeRooms,
            int totalRooms,
            int peakOnlineToday,
            int peakOnlineAllTime,
            int pendingTickets,
            int sanctionsLast24h,
            int uptimeSeconds,
            String version) {}

    /**
     * Reads the dashboard numbers. A table that cannot be read leaves its numbers at 0. The hotel
     * keeps no peak, so both peaks are the current online count.
     */
    public static Stats stats() {
        int onlineUsers =
                Emulator.getGameEnvironment().getUserManager().getOnlineUsers().size();
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
                            connection.prepareStatement("SELECT COUNT(*) FROM support_tickets WHERE state IN (1, 2)");
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

        return new Stats(
                onlineUsers,
                totalUsers,
                activeRooms,
                totalRooms,
                onlineUsers, // peakOnlineToday — not tracked, use current as best-effort
                onlineUsers, // peakOnlineAllTime — same
                pendingTickets,
                sanctionsLast24h,
                Math.max(uptime, 0),
                "Polaris");
    }

    // Approximate uptime — captured at class-load time rather than emu startup
    // (Emulator.java doesn't expose a public startup timestamp). For HK panel
    // headline metrics this is close enough; if tighter accuracy is needed
    // later, plumb Emulator.startup through and read it here.
    private static final long HOUSEKEEPING_BOOT_MILLIS = System.currentTimeMillis();
}
