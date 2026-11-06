package polaris.messages.outgoing.events.mysticbox;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MysticBoxStartOpenComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MysticBoxStartOpenComposer);
        return this.response;
    }
}
