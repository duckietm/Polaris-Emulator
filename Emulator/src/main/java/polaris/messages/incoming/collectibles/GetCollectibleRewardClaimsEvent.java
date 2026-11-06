package polaris.messages.incoming.collectibles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import polaris.hotel.collectibles.CollectibleData.Collection;
import polaris.hotel.collectibles.CollectibleData.CollectorScore;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleRewardClaimsComposer;

/**
 * AIR 15 {@code class_3508} reward claims request (2902, {@code wallet}): the level rewards the user can
 * claim in the rewards tab ({@code 3865}).
 */
public class GetCollectibleRewardClaimsEvent extends MessageHandler {
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
            this.client.sendResponse(new CollectibleRewardClaimsComposer(List.of(), wallet));
            return;
        }

        int userId = user.getUserInfo().getId();
        CollectorScore score = service.collectorScore(userId, settings.xpPerLevel());
        Map<String, String> setNames = new HashMap<>();
        for (Collection collection : service.collections()) {
            setNames.put(collection.collectionId(), collection.name() == null ? "" : collection.name());
        }
        this.client.sendResponse(new CollectibleRewardClaimsComposer(
                service.openRewards(userId, score.level(), settings.xpPerLevel(), CollectiblesSupport.now()),
                CollectiblesService.walletOf(userId),
                setNames));
    }
}
