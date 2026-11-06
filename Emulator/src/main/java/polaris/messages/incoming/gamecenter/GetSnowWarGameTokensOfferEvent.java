package polaris.messages.incoming.gamecenter;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.SnowWarGameTokensComposer;

/** AIR GetSnowWarGameTokensOfferComposer (980): asks for the token offers. */
public class GetSnowWarGameTokensOfferEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }
        this.client.sendResponse(new SnowWarGameTokensComposer(
                SnowWarManager.getInstance().getTokenOffers().loadOffers()));
    }
}
