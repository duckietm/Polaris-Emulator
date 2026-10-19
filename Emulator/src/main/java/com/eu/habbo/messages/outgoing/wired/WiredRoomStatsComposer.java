package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.habbohotel.wired.core.WiredRoomStats;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR WiredRoomStatsData (1891): {@code double usage, double usage limit, bool heavy}, then floor furni,
 * wall furni and the permanent furni, user and global variables, each count then limit. The usage is the wired
 * cost of the room's current monitor window against its limit; without a room or diagnostics the fields are 0.
 */
public class WiredRoomStatsComposer extends MessageComposer {
    private final WiredRoomDiagnostics.Snapshot snapshot;
    private final WiredRoomStats roomStats;

    public WiredRoomStatsComposer(WiredRoomDiagnostics.Snapshot snapshot, WiredRoomStats roomStats) {
        this.snapshot = snapshot;
        this.roomStats = roomStats;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredRoomStatsComposer);

        WiredRoomStats stats =
                (this.roomStats != null) ? this.roomStats : new WiredRoomStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        boolean hasSnapshot = this.snapshot != null;

        this.response.appendDouble(hasSnapshot ? this.snapshot.getUsageCurrentWindow() : 0d);
        this.response.appendDouble(hasSnapshot ? this.snapshot.getUsageLimitPerWindow() : 0d);
        this.response.appendBoolean(hasSnapshot && this.snapshot.isHeavy());
        this.response.appendInt(stats.getFloorCount());
        this.response.appendInt(stats.getFloorLimit());
        this.response.appendInt(stats.getWallCount());
        this.response.appendInt(stats.getWallLimit());
        this.response.appendInt(stats.getPermanentFurniVariables());
        this.response.appendInt(stats.getPermanentFurniVariablesLimit());
        this.response.appendInt(stats.getPermanentUserVariables());
        this.response.appendInt(stats.getPermanentUserVariablesLimit());
        this.response.appendInt(stats.getPermanentGlobalVariables());
        this.response.appendInt(stats.getPermanentGlobalVariablesLimit());

        return this.response;
    }
}
