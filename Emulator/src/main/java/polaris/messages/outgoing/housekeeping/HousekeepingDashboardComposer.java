package polaris.messages.outgoing.housekeeping;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HousekeepingDashboardComposer extends MessageComposer {
    private final int onlineUsers;
    private final int totalUsers;
    private final int activeRooms;
    private final int totalRooms;
    private final int peakOnlineToday;
    private final int peakOnlineAllTime;
    private final int pendingTickets;
    private final int sanctionsLast24h;
    private final int serverUptimeSeconds;
    private final String serverVersion;

    public HousekeepingDashboardComposer(
            int onlineUsers,
            int totalUsers,
            int activeRooms,
            int totalRooms,
            int peakOnlineToday,
            int peakOnlineAllTime,
            int pendingTickets,
            int sanctionsLast24h,
            int serverUptimeSeconds,
            String serverVersion) {
        this.onlineUsers = onlineUsers;
        this.totalUsers = totalUsers;
        this.activeRooms = activeRooms;
        this.totalRooms = totalRooms;
        this.peakOnlineToday = peakOnlineToday;
        this.peakOnlineAllTime = peakOnlineAllTime;
        this.pendingTickets = pendingTickets;
        this.sanctionsLast24h = sanctionsLast24h;
        this.serverUptimeSeconds = serverUptimeSeconds;
        this.serverVersion = serverVersion != null ? serverVersion : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingDashboardComposer);
        this.response.appendInt(this.onlineUsers);
        this.response.appendInt(this.totalUsers);
        this.response.appendInt(this.activeRooms);
        this.response.appendInt(this.totalRooms);
        this.response.appendInt(this.peakOnlineToday);
        this.response.appendInt(this.peakOnlineAllTime);
        this.response.appendInt(this.pendingTickets);
        this.response.appendInt(this.sanctionsLast24h);
        this.response.appendInt(this.serverUptimeSeconds);
        this.response.appendString(this.serverVersion);

        return this.response;
    }
}
