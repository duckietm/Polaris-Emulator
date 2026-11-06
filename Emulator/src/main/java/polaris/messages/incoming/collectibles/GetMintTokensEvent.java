package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleMintTokensComposer;

/** AIR 15 {@code class_3766} stamp balance request (2610, {@code wallet}): answers {@code 9422}. */
public class GetMintTokensEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        String wallet = this.packet.readString();

        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        if (service == null) return;

        int tokens = CollectiblesSupport.ownsWallet(user, wallet)
                ? service.mintTokens(user.getUserInfo().getId())
                : 0;
        this.client.sendResponse(new CollectibleMintTokensComposer(tokens));
    }
}
