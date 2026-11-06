package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredErrorLogs;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredErrorLogsComposer;

/**
 * Official AIR wired menu monitor "Clear all" (1632, no fields). Only a user who may modify the room's wired clears
 * the monitor's errors (and its history); the answer is the error list, now at zero ({@link WiredErrorLogsComposer},
 * 15), so the open tab refreshes at once.
 */
public class WiredErrorLogsClearEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canModifyWired(this.client.getUser())) {
            return;
        }

        WiredManager.clearDiagnosticsLogs(room.getId());

        this.client.sendResponse(new WiredErrorLogsComposer(
                WiredErrorLogs.rows(WiredManager.getDiagnosticsSnapshot(room.getId()), System.currentTimeMillis())));
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}
