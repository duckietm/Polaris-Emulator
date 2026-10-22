package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintTokensComposer;

/** AIR 15 {@code class_3766} stamp balance request (2610, {@code wallet}): answers {@code 9422}. */
public class GetMintTokensEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String wallet = this.packet.readString();

        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        if (service == null) return;

        int tokens = CollectiblesSupport.ownsWallet(habbo, wallet)
                ? service.mintTokens(habbo.getHabboInfo().getId())
                : 0;
        this.client.sendResponse(new CollectibleMintTokensComposer(tokens));
    }
}
