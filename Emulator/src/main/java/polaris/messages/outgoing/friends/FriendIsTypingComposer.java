package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class FriendIsTypingComposer extends MessageComposer {
    private final int senderId;
    private final boolean typing;

    public FriendIsTypingComposer(int senderId, boolean typing) {
        this.senderId = senderId;
        this.typing = typing;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.FriendIsTypingComposer);
        response.appendInt(senderId);
        response.appendBoolean(typing);
        return response;
    }
}
