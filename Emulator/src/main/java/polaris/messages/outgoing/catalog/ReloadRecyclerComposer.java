package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ReloadRecyclerComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ReloadRecyclerComposer);
        this.response.appendInt(1);
        this.response.appendInt(0);
        return this.response;
    }
}
