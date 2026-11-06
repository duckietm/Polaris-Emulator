package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomSettingsUpdatedComposer extends MessageComposer {
    private final Room room;

    public RoomSettingsUpdatedComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomSettingsUpdatedComposer);
        this.response.appendInt(this.room.getId());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
