package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomAddRightsListComposer extends MessageComposer {
    private final Room room;
    private final int userId;
    private final String userName;

    public RoomAddRightsListComposer(Room room, int userId, String username) {
        this.room = room;
        this.userId = userId;
        this.userName = username;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomAddRightsListComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendInt(this.userId);
        this.response.appendString(this.userName);
        return this.response;
    }

    public Room getRoom() {
        return room;
    }

    public int getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }
}
