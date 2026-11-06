package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleMintTokenOffersComposer;

/** AIR 15 stamp offers request (1133): the stamp packs sold for silver ({@code 745}). */
public class GetMintTokenOffersEvent extends MessageHandler {
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

        this.client.sendResponse(new CollectibleMintTokenOffersComposer(service.mintTokenOffers()));
    }
}
