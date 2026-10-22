package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Mintable;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleMintablesComposer;
import java.util.ArrayList;
import java.util.List;

/** AIR 15 relic list request (1606): the furni that can be turned into relics ({@code 9423}). */
public class GetMintableItemsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        if (service == null) return;

        List<CollectibleMintablesComposer.Entry> entries = new ArrayList<>();
        for (Mintable mintable : service.mintables()) {
            Item base = CollectiblesSupport.baseItem(mintable.baseItemId());
            if (base == null) continue;
            entries.add(new CollectibleMintablesComposer.Entry(
                    mintable, base.getSpriteId(), base.getType() == FurnitureType.WALL));
        }
        this.client.sendResponse(new CollectibleMintablesComposer(entries));
    }
}
