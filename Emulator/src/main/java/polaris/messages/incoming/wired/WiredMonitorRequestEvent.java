package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredMonitorDataComposer;

public class WiredMonitorRequestEvent extends MessageHandler {
    private static final int ACTION_FETCH = 0;
    private static final int ACTION_CLEAR_LOGS = 1;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (!room.canInspectWired(this.client.getUser())) {
            return;
        }

        int action = ACTION_FETCH;

        if (this.packet.bytesAvailable() >= 4) {
            action = this.packet.readInt();
        }

        if ((action == ACTION_CLEAR_LOGS) && room.canModifyWired(this.client.getUser())) {
            WiredManager.clearDiagnosticsLogs(room.getId());
        }

        this.client.sendResponse(new WiredMonitorDataComposer(WiredManager.getDiagnosticsSnapshot(room.getId())));
    }

    @Override
    public int getRatelimit() {
        return 200;
    }
}
