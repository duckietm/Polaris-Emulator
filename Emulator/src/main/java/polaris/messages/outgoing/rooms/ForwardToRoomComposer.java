package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ForwardToRoomComposer extends MessageComposer {
    private final int roomId;

    public ForwardToRoomComposer(int roomId) {
        this.roomId = roomId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ForwardToRoomComposer);
        this.response.appendInt(this.roomId);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }
}
