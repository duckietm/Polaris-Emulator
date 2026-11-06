package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.UnsupportedOutgoing;

public class RoomEntryInfoComposer extends MessageComposer {
    private final Room room;

    public RoomEntryInfoComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(UnsupportedOutgoing.RoomEntryInfoComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendString(this.room.getOwnerName());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
