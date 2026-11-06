package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserDanceComposer extends MessageComposer {
    private final RoomUnit roomUnit;

    public RoomUserDanceComposer(RoomUnit roomUnit) {
        this.roomUnit = roomUnit;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserDanceComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendInt(this.roomUnit.getDanceType().getType());
        return this.response;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }
}
