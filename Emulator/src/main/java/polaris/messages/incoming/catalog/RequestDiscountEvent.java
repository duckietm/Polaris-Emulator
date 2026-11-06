package polaris.messages.incoming.catalog;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.DiscountComposer;

public class RequestDiscountEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new DiscountComposer());
    }
}
