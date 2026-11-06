package polaris.messages.incoming.catalog.recycler;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.ReloadRecyclerComposer;

public class ReloadRecyclerEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new ReloadRecyclerComposer());
    }
}
