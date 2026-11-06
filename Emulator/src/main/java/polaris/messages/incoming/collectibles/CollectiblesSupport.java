package polaris.messages.incoming.collectibles;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.collectibles.CollectibleData;
import polaris.hotel.collectibles.CollectibleData.CollectorScore;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.hotel.collectibles.CollectiblesRuntime;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.collectibles.CollectibleEmeraldBalanceComposer;
import polaris.messages.outgoing.collectibles.CollectibleSilverBalanceComposer;
import polaris.messages.outgoing.collectibles.CollectiblesInventoryUpdateComposer;
import polaris.messages.outgoing.collectibles.CollectorScoreComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

/** What every collectibles handler shares: the feature gate, wallets, balances and handing out tokens. */
final class CollectiblesSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectiblesSupport.class);

    private CollectiblesSupport() {}

    /** The live {@code hotel.collectibles.*} settings. */
    static CollectiblesSettings settings() {
        return CollectiblesRuntime.currentSettings();
    }

    /** The service while {@code hotel.collectibles.enabled} is on, else null. */
    static CollectiblesService service(CollectiblesSettings settings) {
        CollectiblesRuntime runtime = CollectiblesRuntime.installed();
        return settings.enabled() && runtime != null ? runtime.service() : null;
    }

    static int now() {
        return CollectiblesRuntime.now();
    }

    /** An online user by id, or null. */
    static User onlineUser(int userId) {
        CollectiblesRuntime runtime = CollectiblesRuntime.installed();
        return runtime == null ? null : runtime.onlineUser().apply(userId);
    }

    /** The furni type behind a relic or a collectible, or null. */
    static Item baseItem(int baseItemId) {
        return CollectiblesRuntime.baseItem(baseItemId);
    }

    /** The client names a wallet in most requests; the only valid one is the user's collector wallet. */
    static boolean ownsWallet(User user, String wallet) {
        return wallet == null
                || wallet.isEmpty()
                || wallet.equals(CollectiblesService.walletOf(user.getUserInfo().getId()));
    }

    static void sendBalances(User user, CollectiblesSettings settings) {
        user.getClient()
                .sendResponse(new CollectibleEmeraldBalanceComposer(
                        user.getUserInfo().getCurrencyAmount(settings.emeraldType())));
        user.getClient()
                .sendResponse(new CollectibleSilverBalanceComposer(
                        user.getUserInfo().getCurrencyAmount(settings.silverType())));
    }

    static void sendScore(User user, CollectiblesService service, CollectiblesSettings settings) throws SQLException {
        CollectorScore score = service.collectorScore(user.getUserInfo().getId(), settings.xpPerLevel());
        user.getClient().sendResponse(new CollectorScoreComposer(score.score(), score.highestScore(), score.level()));
    }

    /** Takes a currency through the audited ledger; false when the user cannot pay. */
    static boolean pay(User user, int type, int amount, String reason) {
        if (amount <= 0) return true;
        return user.tryTakePoints(
                type,
                amount,
                reason,
                EconomyOperationId.create("collectibles:" + user.getUserInfo().getId() + ":" + reason),
                user.getUserInfo().getId());
    }

    static void refund(User user, int type, int amount, String reason) {
        if (amount <= 0) return;
        user.givePoints(
                type,
                amount,
                reason,
                EconomyOperationId.create("collectibles:" + user.getUserInfo().getId() + ":" + reason),
                user.getUserInfo().getId());
    }

    /**
     * After tokens were minted to an online user: hands out their furni, badge or effect (when
     * {@code hotel.collectibles.deliver_items} is on), pushes them into the collectibles inventory and
     * sends the new collector score.
     */
    static void publishAcquired(
            User user, List<Owned> minted, CollectiblesService service, CollectiblesSettings settings)
            throws SQLException {
        if (user == null || user.getClient() == null || minted.isEmpty()) return;
        Map<String, Product> products = service.products();
        if (settings.deliverItems()) {
            for (Owned token : minted) {
                Product product = products.get(token.productCode());
                if (product != null) deliver(user, product);
            }
        }
        user.getClient().sendResponse(new CollectiblesInventoryUpdateComposer(minted, List.of(), products));
        sendScore(user, service, settings);
        if (CollectibleWardrobe.hasClothing(minted, products)) CollectibleWardrobe.refresh(user);
    }

    /** Deletes a furni the user gave up (a minted relic's furni, an opened reward box). */
    static void deleteItem(UserItem item) {
        CollectiblesRuntime runtime = CollectiblesRuntime.installed();
        if (runtime != null && runtime.items() != null) runtime.items().deleteItem(item);
    }

    /** The usable item behind a collectible: its furni (floor, wall, clothing), badge or effect. */
    static void deliver(User user, Product product) {
        try {
            switch (product.productType()) {
                case CollectibleData.TYPE_FLOOR, CollectibleData.TYPE_WALL, CollectibleData.TYPE_CLOTHING -> {
                    CollectiblesRuntime runtime = CollectiblesRuntime.installed();
                    ItemManager items = runtime == null ? null : runtime.items();
                    Item base = items == null ? null : items.getItem(Integer.parseInt(product.itemTypeId()));
                    if (base == null) return;
                    UserItem item = items.createItem(user.getUserInfo().getId(), base, 0, 0, "");
                    if (item == null) return;
                    user.getInventory().getItemsComponent().addItem(item);
                    user.getClient().sendResponse(new AddUserItemComposer(item));
                    user.getClient().sendResponse(new InventoryRefreshComposer());
                }
                case CollectibleData.TYPE_BADGE -> {
                    if (!product.itemTypeId().isEmpty()) user.addBadge(product.itemTypeId());
                }
                case CollectibleData.TYPE_EFFECT -> {
                    int effectId = Integer.parseInt(product.itemTypeId());
                    if (!user.getInventory().getEffectsComponent().ownsPermanently(effectId)) {
                        user.getInventory().getEffectsComponent().createEffect(effectId, 0);
                    }
                }
                default -> {
                    // chat styles and pets stay tokens only
                }
            }
        } catch (NumberFormatException exception) {
            LOGGER.warn(
                    "Collectible product {} has a non-numeric item_type_id {}",
                    product.productCode(),
                    product.itemTypeId());
        }
    }
}
