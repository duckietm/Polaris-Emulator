package com.eu.habbo.messages.incoming.rarevalues;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rarevalues.RareValuesComposer;

public class RequestRareValuesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 5000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        CatalogManager catalog = Emulator.getGameEnvironment().getCatalogManager();
        byte[] snapshot = catalog.getRareValuesPayloadSnapshot();
        if (snapshot != null) {
            this.client.sendResponse(new RareValuesComposer(snapshot));
            return;
        }

        this.client.sendResponse(new RareValuesComposer(catalog.getFurnitureValues()));
    }
}
