package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserTypingComposer extends MessageComposer {
    private final RoomUnit roomUnit;
    private final boolean typing;

    public RoomUserTypingComposer(RoomUnit roomUnit, boolean typing) {
        this.roomUnit = roomUnit;
        this.typing = typing;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserTypingComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendInt(this.typing ? 1 : 0);
        return this.response;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }

    public boolean isTyping() {
        return typing;
    }
}
