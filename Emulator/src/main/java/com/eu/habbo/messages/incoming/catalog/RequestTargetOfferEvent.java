package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.TargetedOfferComposer;

/**
 * Sends the offer that is running to whoever asks for it. The hotel already pushes it once at login;
 * the client asks again every time the offer window opens, and until now nothing answered, so the
 * window stayed empty for anybody who closed and reopened it.
 */
public class RequestTargetOfferEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || TargetOffer.ACTIVE_TARGET_OFFER_ID <= 0) return;

        TargetOffer offer =
                Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(TargetOffer.ACTIVE_TARGET_OFFER_ID);

        if (offer == null) return;

        this.client.sendResponse(new TargetedOfferComposer(this.client.getHabbo(), offer));
    }
}
