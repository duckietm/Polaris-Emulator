package polaris.messages.outgoing.polls;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PollErrorComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PollErrorComposer);
        // EMpty Body
        return this.response;
    }
}
