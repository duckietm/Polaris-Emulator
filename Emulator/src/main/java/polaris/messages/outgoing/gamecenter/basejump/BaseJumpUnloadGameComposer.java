package polaris.messages.outgoing.gamecenter.basejump;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BaseJumpUnloadGameComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BaseJumpUnloadGameComposer);
        this.response.appendInt(3);
        this.response.appendString("basejump");
        return this.response;
    }
}
