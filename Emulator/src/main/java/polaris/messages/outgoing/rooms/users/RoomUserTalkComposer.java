package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomChatMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserTalkComposer extends MessageComposer {
    private final RoomChatMessage roomChatMessage;

    public RoomUserTalkComposer(RoomChatMessage roomChatMessage) {
        this.roomChatMessage = roomChatMessage;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserTalkComposer);

        if (this.roomChatMessage.getMessage().isEmpty()) return null;

        this.roomChatMessage.serialize(this.response);
        return this.response;
    }

    public RoomChatMessage getRoomChatMessage() {
        return roomChatMessage;
    }
}
