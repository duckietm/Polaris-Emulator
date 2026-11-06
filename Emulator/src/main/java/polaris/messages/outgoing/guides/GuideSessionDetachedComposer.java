package polaris.messages.outgoing.guides;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionDetachedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionDetachedComposer);
        return this.response;
    }
}
