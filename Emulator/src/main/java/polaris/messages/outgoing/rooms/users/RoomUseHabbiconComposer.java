package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUseHabbiconComposer extends MessageComposer {
    private final RoomUnit roomUnit;
    private final int habbiconId;

    public RoomUseHabbiconComposer(RoomUnit roomUnit, int habbiconId) {
        this.roomUnit = roomUnit;
        this.habbiconId = habbiconId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUseHabbiconComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendInt(this.habbiconId);
        return this.response;
    }
}
