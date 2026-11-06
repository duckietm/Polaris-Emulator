package polaris.messages.incoming.catalog;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;

public class RequestGiftConfigurationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new GiftConfigurationComposer());
    }
}
