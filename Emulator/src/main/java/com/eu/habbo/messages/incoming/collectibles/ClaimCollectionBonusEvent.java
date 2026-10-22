package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectionBonusClaimResultComposer;
import java.util.List;
import java.util.Optional;

/**
 * AIR 15 bonus claim (149, {@code collectionId, wallet}): the time-limited bonus of a complete set, once
 * per user. Answers {@code 1048 (collectionId, wallet, success)}.
 */
public class ClaimCollectionBonusEvent extends MessageHandler {
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
                    habbo.getHabboInfo().getId(), collectionId, true, CollectiblesSupport.now());
        }
        if (owned.isPresent()) CollectiblesSupport.publishAcquired(habbo, List.of(owned.get()), service, settings);

        this.client.sendResponse(new CollectionBonusClaimResultComposer(collectionId, wallet, owned.isPresent()));
    }
}
