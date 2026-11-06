package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormUserChatMessageComposer extends MessageComposer {

    private final int objectId;
    private final String message;

    public SnowStormUserChatMessageComposer(int objectId, String message) {
        this.objectId = objectId;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormUserChatMessageComposer);
        this.response.appendInt(this.objectId);
        this.response.appendString(this.message);
        return this.response;
    }
}
