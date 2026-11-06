package polaris.messages.incoming.catalog;

import polaris.messages.incoming.MessageHandler;

public class RequestCatalogIndexEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        // this.client.sendResponse(new CatalogPagesListComposer(this.client.getUser(), "NORMAL"));
    }
}
