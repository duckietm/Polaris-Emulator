package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Collection;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectorScore;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleRewardClaimsComposer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        if (!CollectiblesSupport.ownsWallet(habbo, wallet)) {
            this.client.sendResponse(new CollectibleRewardClaimsComposer(List.of(), wallet));
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        CollectorScore score = service.collectorScore(userId, settings.xpPerLevel());
        Map<String, String> setNames = new HashMap<>();
        for (Collection collection : service.collections()) {
            setNames.put(collection.collectionId(), collection.name() == null ? "" : collection.name());
        }
        this.client.sendResponse(new CollectibleRewardClaimsComposer(
                service.openRewards(userId, score.level(), CollectiblesSupport.now()),
                CollectiblesService.walletOf(userId),
                setNames));
    }
}
