package polaris.messages.incoming.catalog.recycler;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.RecyclerLogicComposer;

public class RequestRecyclerLogicEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new RecyclerLogicComposer());
    }
}
