package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.UserNftWardrobeComposer;

/**
 * AIR 15 {@code GetUserNftWardrobeMessageComposer} (3206, no fields), sent by {@code NftAvatarsModel} when the
 * avatar editor builds its NFT tab: answers the user's avatar collectibles ({@code 2236}).
 */
public class GetUserNftWardrobeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || !settings.avatarsActive()) return;

        this.client.sendResponse(new UserNftWardrobeComposer(
                service.avatars().wardrobe(habbo.getHabboInfo().getId())));
    }
}
