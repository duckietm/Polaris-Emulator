package polaris.messages.outgoing.navigator;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomCreatedComposer extends MessageComposer {
    private final Room room;

    public RoomCreatedComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomCreatedComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendString(this.room.getName());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
