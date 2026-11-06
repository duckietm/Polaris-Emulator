package polaris.messages.incoming.catalog;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;

public class RequestMarketplaceConfigEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new MarketplaceConfigComposer());
    }
}
