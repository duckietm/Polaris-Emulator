package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.UserNftWardrobeComposer;

/**
 * AIR 15 {@code GetUserNftWardrobeMessageComposer} (3206, no fields), sent by {@code NftAvatarsModel} when the
 * avatar editor builds its NFT tab: answers the user's avatar collectibles ({@code 2236}).
 */
public class GetUserNftWardrobeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || !settings.avatarsActive()) return;

        this.client.sendResponse(new UserNftWardrobeComposer(
                service.avatars().wardrobe(user.getUserInfo().getId())));
    }
}
