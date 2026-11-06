package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * This profile is not what it was a moment ago. A client showing it asks for it again; one showing
 * somebody else's ignores the packet.
 */
public class ExtendedProfileChangedComposer extends MessageComposer {
    private final int userId;

    public ExtendedProfileChangedComposer(int userId) {
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ExtendedProfileChangedComposer);
        this.response.appendInt(this.userId);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }
}
