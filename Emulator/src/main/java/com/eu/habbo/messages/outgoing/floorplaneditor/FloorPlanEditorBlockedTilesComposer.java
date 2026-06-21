package com.eu.habbo.messages.outgoing.floorplaneditor;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Set;

public class FloorPlanEditorBlockedTilesComposer extends MessageComposer {
    private final Room room;

    public FloorPlanEditorBlockedTilesComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FloorPlanEditorBlockedTilesComposer);

        Set<RoomTile> tileList = this.room.getLockedTiles();

        this.response.appendInt(tileList.size());
        for (RoomTile node : tileList) {
            this.response.appendInt((int) node.x);
            this.response.appendInt((int) node.y);
        }

        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
