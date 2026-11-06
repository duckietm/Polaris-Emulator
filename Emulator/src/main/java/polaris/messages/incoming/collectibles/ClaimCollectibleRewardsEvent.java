package polaris.messages.incoming.collectibles;

import java.util.List;
import polaris.hotel.collectibles.CollectibleData.CollectorScore;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleClaimAllResultComposer;

/**
 * AIR 15 {@code class_2774} claim all (3836, two strings the client leaves empty): claims every open
 * level reward. Answers {@code 3720}: 0 when something was claimed, 1 otherwise.
 */
public class ClaimCollectibleRewardsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        this.packet.readString();
        this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int userId = user.getUserInfo().getId();
        CollectorScore score = service.collectorScore(userId, settings.xpPerLevel());
        List<Owned> minted =
                service.claimLevelRewards(userId, score.level(), settings.xpPerLevel(), CollectiblesSupport.now());
        CollectiblesSupport.publishAcquired(user, minted, service, settings);

        this.client.sendResponse(new CollectibleClaimAllResultComposer(minted.isEmpty() ? 1 : 0));
    }
}
