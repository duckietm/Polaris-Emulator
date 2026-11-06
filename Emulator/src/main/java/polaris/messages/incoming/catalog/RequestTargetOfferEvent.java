package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.TargetOffer;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.TargetedOfferComposer;

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
        if (this.client.getUser() == null || TargetOffer.ACTIVE_TARGET_OFFER_ID <= 0) return;

        TargetOffer offer =
                Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(TargetOffer.ACTIVE_TARGET_OFFER_ID);

        if (offer == null) return;

        this.client.sendResponse(new TargetedOfferComposer(this.client.getUser(), offer));
    }
}
