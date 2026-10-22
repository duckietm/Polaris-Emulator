package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintTokenOffersComposer;

/** AIR 15 stamp offers request (1133): the stamp packs sold for silver ({@code 745}). */
public class GetMintTokenOffersEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        if (service == null) return;

        this.client.sendResponse(new CollectibleMintTokenOffersComposer(service.mintTokenOffers()));
    }
}
