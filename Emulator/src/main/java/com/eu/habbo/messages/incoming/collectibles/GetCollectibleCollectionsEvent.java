package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleCollectionsComposer;
import java.util.List;

/** AIR 15 collections request (1656, {@code wallet}): the user's sets for the collections tab. */
public class GetCollectibleCollectionsEvent extends MessageHandler {
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
            this.client.sendResponse(new CollectibleCollectionsComposer(List.of()));
            return;
        }

        this.client.sendResponse(new CollectibleCollectionsComposer(
                service.collectionStates(habbo.getHabboInfo().getId(), CollectiblesSupport.now())));
    }
}
