package polaris.messages.outgoing.gamecenter.basejump;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BaseJumpLeaveQueueComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BaseJumpLeaveQueueComposer);
        this.response.appendInt(3);
        return this.response;
    }
}
