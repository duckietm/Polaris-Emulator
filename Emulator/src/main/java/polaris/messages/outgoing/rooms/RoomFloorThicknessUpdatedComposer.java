package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomFloorThicknessUpdatedComposer extends MessageComposer {
    private final Room room;

    public RoomFloorThicknessUpdatedComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomFloorThicknessUpdatedComposer);
        this.response.appendBoolean(this.room.isHideWall()); // Hide walls?
        this.response.appendInt(this.room.getFloorSize()); // Floor Thickness
        this.response.appendInt(this.room.getWallSize()); // Wall Thickness
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
