package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HanditemBlockStateComposer extends MessageComposer {
    private final int roomId;
    private final boolean blocked;

    public HanditemBlockStateComposer(Room room) {
        this.roomId = (room != null) ? room.getId() : 0;
        this.blocked = RoomHanditemBlockSupport.isHanditemBlocked(room);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HanditemBlockStateComposer);
        this.response.appendInt(this.roomId);
        this.response.appendBoolean(this.blocked);
        return this.response;
    }
}
