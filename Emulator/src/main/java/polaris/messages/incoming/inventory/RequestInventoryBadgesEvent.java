package polaris.messages.incoming.inventory;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryBadgesComposer;

public class RequestInventoryBadgesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new InventoryBadgesComposer(this.client.getUser()));
    }
}
