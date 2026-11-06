package polaris.messages.incoming.catalog.marketplace;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceOwnItemsComposer;

public class RequestOwnItemsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new MarketplaceOwnItemsComposer(this.client.getUser()));
    }
}
