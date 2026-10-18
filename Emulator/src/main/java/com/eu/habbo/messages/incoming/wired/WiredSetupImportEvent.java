package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.share.WiredSetupShare;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredSetupImportResultComposer;

/**
 * Wired setup import: previews or applies a share code. Needs the modify wired right; the code is
 * validated in full on the server and only configures boxes the importer placed or places from their
 * own inventory.
 */
public final class WiredSetupImportEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String code = this.packet.readString();
        int anchorMode = this.packet.readInt();
        boolean placeFromInventory = this.packet.readBoolean();
        boolean confirm = this.packet.readBoolean();
        Room room = currentRoom();
        if (room == null) return;

        if (!room.canModifyWired(this.client.getHabbo())) {
            this.client.sendResponse(new WiredSetupImportResultComposer(
                    WiredSetupShare.refusedImport(WiredSetupShare.STATUS_NO_RIGHTS, confirm)));
            return;
        }

        int anchor = anchorMode == WiredSetupShare.ANCHOR_AVATAR
                ? WiredSetupShare.ANCHOR_AVATAR
                : WiredSetupShare.ANCHOR_ORIGINAL;
        this.client.sendResponse(new WiredSetupImportResultComposer(
                WiredSetupShare.importSetup(room, this.client, code, anchor, placeFromInventory, confirm)));
    }

    @Override
    public int getRatelimit() {
        // One confirmed import saves up to 300 boxes; a manual save is one box per 200 ms.
        return 3000;
    }

    @Override
    public String getRatelimitGroup() {
        return "wired.share";
    }
}
