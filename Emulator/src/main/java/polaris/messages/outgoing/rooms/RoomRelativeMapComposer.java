package polaris.messages.outgoing.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomRelativeMapComposer extends MessageComposer {
    private final Room room;

    public RoomRelativeMapComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomRelativeMapComposer);
        this.response.appendInt(
                this.room.getLayout().getMapSize() / this.room.getLayout().getMapSizeY());
        this.response.appendInt(this.room.getLayout().getMapSize());
        for (short y = 0; y < this.room.getLayout().getMapSizeY(); y++) {
            for (short x = 0; x < this.room.getLayout().getMapSizeX(); x++) {
                RoomTile t = this.room.getLayout().getTile(x, y);

                if (t != null) {
                    if (Emulator.getConfig().getBoolean("custom.stacking.enabled")) {
                        this.response.appendShort((short) (t.z * 256.0));
                    } else {
                        this.response.appendShort(t.relativeHeight());
                    }
                } else {
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
