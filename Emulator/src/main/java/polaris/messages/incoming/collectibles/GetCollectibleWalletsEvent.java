package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleWalletsComposer;

/**
 * AIR 15 wallets request (3702), sent when the collectibles hub opens. Answers the user's collector
 * wallet ({@code 400}) and the emerald and silver balances.
 */
public class GetCollectibleWalletsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        if (CollectiblesSupport.service(settings) == null) return;

        this.client.sendResponse(new CollectibleWalletsComposer(
                CollectiblesService.walletOf(user.getUserInfo().getId())));
        CollectiblesSupport.sendBalances(user, settings);
    }
}
