package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomPaneComposer extends MessageComposer {
    private final Room room;
    private final boolean roomOwner;

    public RoomPaneComposer(Room room, boolean roomOwner) {
        this.room = room;
        this.roomOwner = roomOwner;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomPaneComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendBoolean(this.roomOwner);
        return this.response;
    }

    public Room getRoom() {
        return room;
    }

    public boolean isRoomOwner() {
        return roomOwner;
    }
}
