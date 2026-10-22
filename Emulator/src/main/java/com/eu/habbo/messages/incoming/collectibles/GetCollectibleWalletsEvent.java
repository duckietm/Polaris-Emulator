package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleWalletsComposer;

/**
 * AIR 15 wallets request (3702), sent when the collectibles hub opens. Answers the user's collector
 * wallet ({@code 400}) and the emerald and silver balances.
 */
public class GetCollectibleWalletsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        if (CollectiblesSupport.service(settings) == null) return;

        this.client.sendResponse(new CollectibleWalletsComposer(
                CollectiblesService.walletOf(habbo.getHabboInfo().getId())));
        CollectiblesSupport.sendBalances(habbo, settings);
    }
}
