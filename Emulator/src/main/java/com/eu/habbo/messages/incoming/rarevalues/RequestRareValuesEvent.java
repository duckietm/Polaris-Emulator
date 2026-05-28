package com.eu.habbo.messages.incoming.rarevalues;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rarevalues.RareValuesComposer;

// Client requests the furni value map once on load. Public info (catalog prices),
// no permission gate. Rate limited since the payload is large.
public class RequestRareValuesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 5000;
    }

    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new RareValuesComposer(
                Emulator.getGameEnvironment().getCatalogManager().getFurnitureValues()
        ));
    }
}
