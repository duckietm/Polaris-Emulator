package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserUnbannedComposer extends MessageComposer {
    private final Room room;
    private final int userId;

    public RoomUserUnbannedComposer(Room room, int userId) {
        this.room = room;
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserUnbannedComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendInt(this.userId);
        return this.response;
    }

    public Room getRoom() {
        return room;
    }

    public int getUserId() {
        return userId;
    }
}
