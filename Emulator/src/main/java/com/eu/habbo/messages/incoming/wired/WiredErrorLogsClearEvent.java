package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredErrorLogs;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredErrorLogsComposer;

/**
 * Official AIR wired menu monitor "Clear all" (1632, no fields). Only a user who may modify the room's wired clears
 * the monitor's errors (and its history); the answer is the error list, now at zero ({@link WiredErrorLogsComposer},
 * 15), so the open tab refreshes at once.
 */
public class WiredErrorLogsClearEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canModifyWired(this.client.getHabbo())) {
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
