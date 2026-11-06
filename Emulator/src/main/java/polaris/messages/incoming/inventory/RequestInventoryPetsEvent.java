package polaris.messages.incoming.inventory;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryPetsComposer;

public class RequestInventoryPetsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new InventoryPetsComposer(this.client.getUser()));
    }
}
