package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomModelComposer extends MessageComposer {
    private final Room room;

    public RoomModelComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomModelComposer);
        this.response.appendString(this.room.getLayout().getName());
        this.response.appendInt(this.room.getId());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
