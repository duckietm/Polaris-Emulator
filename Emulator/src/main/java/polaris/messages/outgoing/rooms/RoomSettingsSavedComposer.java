package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomSettingsSavedComposer extends MessageComposer {
    private final Room room;

    public RoomSettingsSavedComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomSettingsSavedComposer);
        this.response.appendInt(this.room.getId());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
