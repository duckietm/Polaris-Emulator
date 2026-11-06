package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomInviteComposer extends MessageComposer {
    private final int userId;
    private final String message;

    public RoomInviteComposer(int userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomInviteComposer);
        this.response.appendInt(this.userId);
        this.response.appendString(this.message);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }
}
