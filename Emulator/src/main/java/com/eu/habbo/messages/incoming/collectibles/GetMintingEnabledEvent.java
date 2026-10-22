package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintingEnabledComposer;

/** AIR 15 minting status request (2371): whether relics can be minted ({@code 3059}). */
public class GetMintingEnabledEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        if (CollectiblesSupport.service(settings) == null) return;

        this.client.sendResponse(new CollectibleMintingEnabledComposer(settings.mintingEnabled()));
    }
}
