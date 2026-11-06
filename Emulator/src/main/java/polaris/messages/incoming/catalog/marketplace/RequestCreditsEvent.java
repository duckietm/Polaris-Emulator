package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.messages.incoming.MessageHandler;

public class RequestCreditsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        MarketPlace.getCredits(this.client);
    }
}
