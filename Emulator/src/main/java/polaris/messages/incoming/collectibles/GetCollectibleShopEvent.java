package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleShopOffersComposer;

/** AIR 15 shop request (1762): the products the guild shop sells for emeralds ({@code 3495}). */
public class GetCollectibleShopEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        if (service == null) return;

        this.client.sendResponse(new CollectibleShopOffersComposer(service.shopOffers()));
    }
}
