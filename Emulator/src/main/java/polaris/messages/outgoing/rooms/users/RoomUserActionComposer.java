package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserAction;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserActionComposer extends MessageComposer {
    private RoomUserAction action;
    private RoomUnit roomUnit;

    public RoomUserActionComposer(RoomUnit roomUnit, RoomUserAction action) {
        this.roomUnit = roomUnit;
        this.action = action;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserActionComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendInt(this.action.getAction());
        return this.response;
    }

    public RoomUserAction getAction() {
        return action;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }
}
