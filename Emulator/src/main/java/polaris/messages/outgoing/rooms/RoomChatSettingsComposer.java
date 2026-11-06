package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomChatSettingsComposer extends MessageComposer {
    private final Room room;

    public RoomChatSettingsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomChatSettingsComposer);
        this.response.appendInt(this.room.getChatMode());
        this.response.appendInt(this.room.getChatWeight());
        this.response.appendInt(this.room.getChatSpeed());
        this.response.appendInt(this.room.getChatDistance());
        this.response.appendInt(this.room.getChatProtection());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
