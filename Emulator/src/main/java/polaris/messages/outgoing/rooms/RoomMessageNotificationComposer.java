package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomMessageNotificationComposer extends MessageComposer {
    private final Room room;
    private final int count;

    public RoomMessageNotificationComposer(Room room, int count) {
        this.room = room;
        this.count = count;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomMessageNotificationComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendString(this.room.getName());
        this.response.appendInt(this.count);
        return this.response;
    }

    public Room getRoom() {
        return room;
    }

    public int getCount() {
        return count;
    }
}
