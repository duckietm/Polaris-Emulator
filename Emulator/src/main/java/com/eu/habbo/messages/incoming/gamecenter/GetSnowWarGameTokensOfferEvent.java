package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.SnowWarGameTokensComposer;

/** AIR GetSnowWarGameTokensOfferComposer (980): asks for the token offers. */
public class GetSnowWarGameTokensOfferEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }
        this.client.sendResponse(new SnowWarGameTokensComposer(
                SnowWarManager.getInstance().getTokenOffers().loadOffers()));
    }
}
