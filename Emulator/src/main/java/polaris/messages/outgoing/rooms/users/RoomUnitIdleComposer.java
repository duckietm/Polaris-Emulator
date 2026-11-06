package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUnitIdleComposer extends MessageComposer {
    private final RoomUnit roomUnit;

    public RoomUnitIdleComposer(RoomUnit roomUnit) {
        this.roomUnit = roomUnit;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUnitIdleComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendBoolean(this.roomUnit.isIdle());
        return this.response;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }
}
