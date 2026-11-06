package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/** AIR 15 wallet selection (3361, {@code wallet}): answers the collector XP and level ({@code 1789}). */
public class SelectCollectibleWalletEvent extends MessageHandler {
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
        if (service == null || !CollectiblesSupport.ownsWallet(user, wallet)) return;

        CollectiblesSupport.sendScore(user, service, settings);
    }
}
