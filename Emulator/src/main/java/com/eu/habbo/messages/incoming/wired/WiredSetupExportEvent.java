package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.share.WiredSetupShare;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredSetupExportComposer;

/** Wired setup share: the share code of every wired box in the room. Needs the modify wired right. */
public final class WiredSetupExportEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int version = this.packet.readInt();
        Room room = currentRoom();
        if (room == null) return;

        if (!room.canModifyWired(this.client.getHabbo())) {
            this.client.sendResponse(new WiredSetupExportComposer(WiredSetupShare.STATUS_NO_RIGHTS, 0, ""));
            return;
        }
        if (version != 1) {
            this.client.sendResponse(new WiredSetupExportComposer(WiredSetupShare.STATUS_UNSUPPORTED_VERSION, 0, ""));
            return;
        }

        WiredSetupShare.Export export = WiredSetupShare.export(room);
        this.client.sendResponse(new WiredSetupExportComposer(export.status(), export.boxCount(), export.code()));
    }

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public String getRatelimitGroup() {
        return "wired.share";
    }
}
