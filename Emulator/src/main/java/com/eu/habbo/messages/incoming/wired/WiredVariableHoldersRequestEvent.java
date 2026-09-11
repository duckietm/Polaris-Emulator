package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredVariableHoldersComposer;

/**
 * Official AIR 13 {@code WiredMenuOverviewTab.requestHolders} (2973): every holder of one wired
 * variable, so the overview tab can highlight them inside the room.
 */
public class WiredVariableHoldersRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String variableId = this.packet.readString();
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getHabbo())) {
            return;
        }

        RoomWiredVariableCatalog.Variable variable = RoomWiredVariableCatalog.byId(
                        RoomWiredVariableCatalog.variables(room))
                .get(variableId);

        if (variable == null) {
            return;
        }

        this.client.sendResponse(new WiredVariableHoldersComposer(
                room.getId(), variable, RoomWiredVariableCatalog.holders(room, variableId)));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
