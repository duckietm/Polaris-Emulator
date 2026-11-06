package polaris.messages.outgoing.events.mysticbox;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MysticBoxCloseComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MysticBoxCloseComposer);
        return this.response;
    }
}
