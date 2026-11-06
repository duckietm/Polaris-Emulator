package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredErrorLogs;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredErrorLogsComposer;

/**
 * Official AIR wired menu monitor error log request (1132, no fields). The monitor tab sends it every 500 ms while it
 * is open, right after the room stats request (1957); the answer is {@link WiredErrorLogsComposer} (15).
 */
public class WiredErrorLogsRequestEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getUser())) {
            return;
        }

        this.client.sendResponse(new WiredErrorLogsComposer(
                WiredErrorLogs.rows(WiredManager.getDiagnosticsSnapshot(room.getId()), System.currentTimeMillis())));
    }

    @Override
    public int getRatelimit() {
        return 200;
    }
}
