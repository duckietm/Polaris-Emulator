package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectorScore;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleClaimAllResultComposer;
import java.util.List;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        this.packet.readString();
        this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int userId = habbo.getHabboInfo().getId();
        CollectorScore score = service.collectorScore(userId, settings.xpPerLevel());
        List<Owned> minted = service.claimLevelRewards(userId, score.level(), CollectiblesSupport.now());
        CollectiblesSupport.publishAcquired(habbo, minted, service, settings);

        this.client.sendResponse(new CollectibleClaimAllResultComposer(minted.isEmpty() ? 1 : 0));
    }
}
