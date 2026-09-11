package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.arrays.WiredCreatorToolsArrayInspection;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredArrayInspectionDataComposer;

public final class WiredArrayInspectionRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || !room.canInspectWired(this.client.getHabbo()) || this.packet.bytesAvailable() < 20) return;

        int variableType = this.packet.readInt();
        int requestedOwnerId = this.packet.readInt();
        int definitionItemId = this.packet.readInt();
        int page = this.packet.readInt();
        int pageSize = this.packet.readInt();
        WiredArrayCreatorToolsSupport.Resolved resolved =
                WiredArrayCreatorToolsSupport.resolve(room, variableType, requestedOwnerId, definitionItemId);
        if (resolved == null) return;

        this.client.sendResponse(new WiredArrayInspectionDataComposer(WiredCreatorToolsArrayInspection.create(
                room,
                resolved.requestedOwnerType(),
                resolved.requestedOwnerId(),
                resolved.definition(),
                resolved.ownerId(),
                page,
                pageSize)));
    }

    @Override
    public int getRatelimit() {
        return 100;
    }
}
