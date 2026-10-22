package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.collectibles.UserNftWardrobeComposer;
import java.sql.SQLException;
import java.util.List;

/** Tokens handed out from outside the collectibles packets (staff commands): what the user gets to see. */
public final class CollectibleGrants {
    private CollectibleGrants() {}

    /**
     * After tokens were minted to an online user: the same as a purchase or a claim (the furni, badge or effect,
     * the collectibles inventory update, the collector score), plus the avatar editor's NFT list when one of them
     * is a wearable look. The official editor replaces its list with every {@code 2236}, so the whole list goes.
     */
    public static void announce(Habbo habbo, List<Owned> minted) throws SQLException {
        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || habbo == null || habbo.getClient() == null || minted.isEmpty()) return;

        CollectiblesSupport.publishAcquired(habbo, minted, service, settings);

        if (!settings.avatarsActive()) return;
        int userId = habbo.getHabboInfo().getId();
        var wardrobe = service.avatars().wardrobe(userId);
        boolean wearable = wardrobe.stream()
                .anyMatch(outfit -> minted.stream()
                        .anyMatch(token -> token.assetId() == outfit.token().assetId()));
        if (wearable) habbo.getClient().sendResponse(new UserNftWardrobeComposer(wardrobe));
    }
}
