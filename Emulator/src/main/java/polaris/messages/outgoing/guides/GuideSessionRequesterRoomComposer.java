package polaris.messages.outgoing.guides;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionRequesterRoomComposer extends MessageComposer {
    private final Room room;

    public GuideSessionRequesterRoomComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionRequesterRoomComposer);
        this.response.appendInt(this.room != null ? this.room.getId() : 0);
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
