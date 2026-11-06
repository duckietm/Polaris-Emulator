package polaris.messages.outgoing.guides;

import polaris.hotel.guides.GuideChatMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionMessageComposer extends MessageComposer {
    private final GuideChatMessage message;

    public GuideSessionMessageComposer(GuideChatMessage message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionMessageComposer);
        this.response.appendString(this.message.message); // Message
        this.response.appendInt(this.message.userId); // Sender ID
        return this.response;
    }

    public GuideChatMessage getMessage() {
        return message;
    }
}
