package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.RoomRightLevels;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomRightsComposer extends MessageComposer {
    private final RoomRightLevels type;

    public RoomRightsComposer(RoomRightLevels type) {
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomRightsComposer);
        this.response.appendInt(this.type.level);
        return this.response;
    }

    public RoomRightLevels getType() {
        return type;
    }
}
