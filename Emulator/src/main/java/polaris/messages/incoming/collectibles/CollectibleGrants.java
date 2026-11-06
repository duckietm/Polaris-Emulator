package polaris.messages.incoming.collectibles;

import java.sql.SQLException;
import java.util.List;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.outgoing.collectibles.UserNftWardrobeComposer;

/** Tokens handed out from outside the collectibles packets (staff commands): what the user gets to see. */
public final class CollectibleGrants {
    private CollectibleGrants() {}

    /**
     * After tokens were minted to an online user: the same as a purchase or a claim (the furni, badge or effect,
     * the collectibles inventory update, the collector score), plus the avatar editor's NFT list when one of them
     * is a wearable look. The official editor replaces its list with every {@code 2236}, so the whole list goes.
     */
    public static void announce(User user, List<Owned> minted) throws SQLException {
        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || user == null || user.getClient() == null || minted.isEmpty()) return;

        CollectiblesSupport.publishAcquired(user, minted, service, settings);

        if (!settings.avatarsActive()) return;
        int userId = user.getUserInfo().getId();
        var wardrobe = service.avatars().wardrobe(userId);
        boolean wearable = wardrobe.stream()
                .anyMatch(outfit -> minted.stream()
                        .anyMatch(token -> token.assetId() == outfit.token().assetId()));
        if (wearable) user.getClient().sendResponse(new UserNftWardrobeComposer(wardrobe));
    }
}
