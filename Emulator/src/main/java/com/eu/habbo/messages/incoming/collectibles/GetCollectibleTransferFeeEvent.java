package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleTransferFeeComposer;

/**
 * AIR 15 {@code class_3623} transfer fee request (official 875, taken here by the floor plan editor:
 * 9421). Answers {@code 3909}: the silver a transfer of every collectible costs.
 */
public class GetCollectibleTransferFeeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int count = service.owned(habbo.getHabboInfo().getId()).size();
        this.client.sendResponse(new CollectibleTransferFeeComposer(TransferCollectiblesEvent.fee(count, settings)));
    }
}
