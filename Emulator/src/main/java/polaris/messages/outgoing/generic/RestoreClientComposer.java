package polaris.messages.outgoing.generic;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RestoreClientComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RestoreClientComposer);
        // Empty body
        return this.response;
    }
}
