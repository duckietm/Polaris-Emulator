package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredObjectInspection;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredVariablesForObjectComposer;

/**
 * Official inspection tab read of one object's variables ({@code wiredmenu/?_-t1r?}): type (0 furni, 1 user,
 * -10 room) and the object id (item id, room index, or 0). Answers with {@link WiredVariablesForObjectComposer}
 * to those who may inspect the room's wired.
 */
public class WiredGetVariablesForObjectEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getHabbo())) {
            return;
        }

        int type = this.packet.readInt();
        int objectId =
                BuildersClubItemIds.fromWire(room, this.packet.readInt(), type == WiredObjectInspection.TYPE_FURNI);
        WiredObjectInspection inspection = WiredObjectInspection.inspect(room, type, objectId);

        if (inspection != null) {
            this.client.sendResponse(new WiredVariablesForObjectComposer(inspection));
        }
    }

    @Override
    public int getRatelimit() {
        return 50;
    }
}
