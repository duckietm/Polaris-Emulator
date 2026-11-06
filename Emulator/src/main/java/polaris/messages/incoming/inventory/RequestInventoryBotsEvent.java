package polaris.messages.incoming.inventory;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryBotsComposer;

public class RequestInventoryBotsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new InventoryBotsComposer(this.client.getUser()));
    }
}
