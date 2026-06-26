package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomRelativeMapComposer extends MessageComposer {
    private final Room room;

    public RoomRelativeMapComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomRelativeMapComposer);
        // Hoist the layout + the synchronized Properties config read out of the mapSizeX*mapSizeY loop
        // (config can't change mid-compose, so emitted bytes are identical).
        final com.eu.habbo.habbohotel.rooms.RoomLayout layout = this.room.getLayout();
        final boolean customStacking = Emulator.getConfig().getBoolean("custom.stacking.enabled");
        this.response.appendInt(layout.getMapSize() / layout.getMapSizeY());
        this.response.appendInt(layout.getMapSize());
        for (short y = 0; y < layout.getMapSizeY(); y++) {
            for (short x = 0; x < layout.getMapSizeX(); x++) {
                RoomTile t = layout.getTile(x, y);

                if (t != null) {
                    if (customStacking) {
                        this.response.appendShort((short) (t.z * 256.0));
                    }
                    else {
                        this.response.appendShort(t.relativeHeight());
                    }
                }
                else {
                    this.response.appendShort(Short.MAX_VALUE);
                }

            }
        }
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
