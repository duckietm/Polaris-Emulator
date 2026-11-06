package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomHeightMapComposer extends MessageComposer {
    private final Room room;

    public RoomHeightMapComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomHeightMapComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(this.room.getWallHeight()); // FixedWallsHeight
        this.response.appendString(this.room.getLayout().getRelativeMap());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
