package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredRoomStats;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredRoomStatsComposer;

/**
 * Official AIR wired menu monitor room stats request (1957, no fields). The monitor tab sends it every 500 ms
 * while it is open, next to the error log request; the answer is {@link WiredRoomStatsComposer} (1891).
 */
public class WiredRoomStatsRequestEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getUser())) {
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
