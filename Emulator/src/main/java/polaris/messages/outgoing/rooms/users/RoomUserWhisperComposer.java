package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomChatMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserWhisperComposer extends MessageComposer {
    private final RoomChatMessage roomChatMessage;

    public RoomUserWhisperComposer(RoomChatMessage roomChatMessage) {
        this.roomChatMessage = roomChatMessage;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.roomChatMessage.getMessage().isEmpty()) return null;

        this.response.init(Outgoing.RoomUserWhisperComposer);
        this.roomChatMessage.serialize(this.response);

        return this.response;
    }

    public RoomChatMessage getRoomChatMessage() {
        return roomChatMessage;
    }
}
