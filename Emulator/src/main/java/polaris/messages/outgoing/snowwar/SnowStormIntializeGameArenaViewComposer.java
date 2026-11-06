package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormIntializeGameArenaViewComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormIntializeGameArenaViewComposer);
        return this.response;
    }
}
