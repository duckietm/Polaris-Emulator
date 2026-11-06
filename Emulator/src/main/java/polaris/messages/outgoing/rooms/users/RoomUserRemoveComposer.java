package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserRemoveComposer extends MessageComposer {
    private final RoomUnit roomUnit;

    public RoomUserRemoveComposer(RoomUnit roomUnit) {
        this.roomUnit = roomUnit;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserRemoveComposer);
        this.response.appendString(this.roomUnit.getId() + "");
        return this.response;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }
}
