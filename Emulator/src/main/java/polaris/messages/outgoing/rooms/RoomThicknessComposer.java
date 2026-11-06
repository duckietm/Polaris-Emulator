package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomThicknessComposer extends MessageComposer {
    private final Room room;

    public RoomThicknessComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomThicknessComposer);
        this.response.appendBoolean(this.room.isHideWall());
        this.response.appendInt(this.room.getWallSize());
        this.response.appendInt(this.room.getFloorSize());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
