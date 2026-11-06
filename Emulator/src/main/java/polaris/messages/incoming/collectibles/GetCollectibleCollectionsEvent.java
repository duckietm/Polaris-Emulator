package polaris.messages.incoming.collectibles;

import java.util.List;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleCollectionsComposer;

/** AIR 15 collections request (1656, {@code wallet}): the user's sets for the collections tab. */
public class GetCollectibleCollectionsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        if (!CollectiblesSupport.ownsWallet(user, wallet)) {
            this.client.sendResponse(new CollectibleCollectionsComposer(List.of()));
            return;
        }

        this.client.sendResponse(new CollectibleCollectionsComposer(
                service.collectionStates(user.getUserInfo().getId(), CollectiblesSupport.now())));
    }
}
