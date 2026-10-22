package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectionRewardClaimResultComposer;
import java.util.List;
import java.util.Optional;

/**
 * AIR 15 completion reward claim (official 2209, taken here by the mannequin: 9420,
 * {@code collectionId, wallet}): the reward for completing a set, once per user. Answers
 * {@code 501 (collectionId, wallet, success)}.
 */
public class ClaimCollectionRewardEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String collectionId = this.packet.readString();
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Optional<Owned> owned = Optional.empty();
        if (CollectiblesSupport.ownsWallet(habbo, wallet)) {
            owned = service.claimCollection(
                    habbo.getHabboInfo().getId(), collectionId, false, CollectiblesSupport.now());
        }
        if (owned.isPresent()) CollectiblesSupport.publishAcquired(habbo, List.of(owned.get()), service, settings);

        this.client.sendResponse(new CollectionRewardClaimResultComposer(collectionId, wallet, owned.isPresent()));
    }
}
