package polaris.messages.incoming.collectibles;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.collectibles.CollectibleData;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.hotel.collectibles.CollectibleFurniTypes;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.messages.outgoing.users.UserClothesComposer;

/**
 * Clothing collectibles in the avatar editor. In AIR the user wears a clothing collectible from the normal
 * clothing tabs for as long as they hold the token: its figure sets come in the owned figure sets packet
 * ({@code FigureSetIdsMessageEvent}), no clothing furni has to be redeemed.
 */
public final class CollectibleWardrobe {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectibleWardrobe.class);

    private CollectibleWardrobe() {}

    /** The figure sets of every clothing collectible among the tokens. */
    public static Set<Integer> figureSetIds(
            List<Owned> owned, Map<String, Product> products, IntFunction<Item> baseItems) {
        Set<Integer> setIds = new LinkedHashSet<>();
        for (Owned token : owned) {
            Product product = products.get(token.productCode());
            if (product == null || product.productType() != CollectibleData.TYPE_CLOTHING) continue;
            setIds.addAll(CollectibleFurniTypes.wireFigureSetIds(product, baseItems));
        }
        return setIds;
    }

    /** Puts the user's clothing collectibles in their wardrobe (none while collectibles are off). */
    public static void load(User user) {
        if (user == null) return;
        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        Set<Integer> setIds = Set.of();
        if (service != null) {
            try {
                setIds = figureSetIds(
                        service.owned(user.getUserInfo().getId()), service.products(), CollectiblesSupport::baseItem);
            } catch (SQLException e) {
                LOGGER.error(
                        "Could not load the clothing collectibles of user {}",
                        user.getUserInfo().getId(),
                        e);
                return;
            }
        }
        user.getInventory().getWardrobeComponent().setCollectibleSets(setIds);
    }

    /** {@link #load} for an online user, then the owned figure sets again so the editor unlocks them. */
    public static void refresh(User user) {
        if (user == null || user.getClient() == null) return;
        load(user);
        user.getClient().sendResponse(new UserClothesComposer(user));
    }

    /** Whether any of the tokens is a clothing collectible. */
    static boolean hasClothing(List<Owned> tokens, Map<String, Product> products) {
        for (Owned token : tokens) {
            Product product = products.get(token.productCode());
            if (product != null && product.productType() == CollectibleData.TYPE_CLOTHING) return true;
        }
        return false;
    }
}
