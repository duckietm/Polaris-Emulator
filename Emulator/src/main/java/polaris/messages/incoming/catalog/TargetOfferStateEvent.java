package polaris.messages.incoming.catalog;

import polaris.hotel.users.cache.UserOfferPurchase;
import polaris.messages.incoming.MessageHandler;

public class TargetOfferStateEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int id = this.packet.readInt();
        int state = this.packet.readInt();

        UserOfferPurchase purchase = this.client.getUser().getUserStats().getUserOfferPurchase(id);
        if (purchase != null) {
            purchase.setState(state);
        }
    }
}
