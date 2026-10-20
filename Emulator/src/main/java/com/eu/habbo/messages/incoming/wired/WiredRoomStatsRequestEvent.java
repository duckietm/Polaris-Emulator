package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomStats;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredRoomStatsComposer;

/**
 * Official AIR wired menu monitor room stats request (1957, no fields). The monitor tab sends it every 500 ms
 * while it is open, next to the error log request; the answer is {@link WiredRoomStatsComposer} (1891).
 */
public class WiredRoomStatsRequestEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getHabbo())) {
            return;
        }

        this.client.sendResponse(
                new WiredRoomStatsComposer(WiredManager.getDiagnosticsSnapshot(room.getId()), WiredRoomStats.of(room)));
    }

    @Override
    public int getRatelimit() {
        return 200;
    }
}
