package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomMutedComposer extends MessageComposer {
    private final Room room;

    public RoomMutedComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomMutedComposer);
        this.response.appendBoolean(this.room.isMuted());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
