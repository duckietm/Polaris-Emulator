package polaris.messages.incoming.rarevalues;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rarevalues.RareValuesComposer;

public class RequestRareValuesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 5000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) return;

        CatalogManager catalog = Emulator.getGameEnvironment().getCatalogManager();
        byte[] snapshot = catalog.getRareValuesPayloadSnapshot();
        if (snapshot != null) {
            this.client.sendResponse(new RareValuesComposer(snapshot));
            return;
        }

        this.client.sendResponse(new RareValuesComposer(catalog.getFurnitureValues()));
    }
}
