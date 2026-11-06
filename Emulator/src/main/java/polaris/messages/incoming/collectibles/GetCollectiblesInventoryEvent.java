package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectiblesInventoryComposer;

/** AIR 15 collectibles inventory request (1119): every token the user owns ({@code 2539}). */
public class GetCollectiblesInventoryEvent extends MessageHandler {
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

        this.client.sendResponse(new CollectiblesInventoryComposer(
                service.owned(user.getUserInfo().getId()), service.products()));
    }
}
