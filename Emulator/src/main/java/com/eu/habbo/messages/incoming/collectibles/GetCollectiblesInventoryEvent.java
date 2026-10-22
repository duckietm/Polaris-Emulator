package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectiblesInventoryComposer;

/** AIR 15 collectibles inventory request (1119): every token the user owns ({@code 2539}). */
public class GetCollectiblesInventoryEvent extends MessageHandler {
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

        this.client.sendResponse(new CollectiblesInventoryComposer(
                service.owned(habbo.getHabboInfo().getId()), service.products()));
    }
}
