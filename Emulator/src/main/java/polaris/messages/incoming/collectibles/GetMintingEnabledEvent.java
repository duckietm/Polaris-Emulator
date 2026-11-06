package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleMintingEnabledComposer;

/** AIR 15 minting status request (2371): whether relics can be minted ({@code 3059}). */
public class GetMintingEnabledEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        if (CollectiblesSupport.service(settings) == null) return;

        this.client.sendResponse(new CollectibleMintingEnabledComposer(settings.mintingEnabled()));
    }
}
