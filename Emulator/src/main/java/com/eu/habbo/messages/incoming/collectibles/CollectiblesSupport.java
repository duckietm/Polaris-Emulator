package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectorScore;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.collectibles.CollectiblesRuntime;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleEmeraldBalanceComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleSilverBalanceComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectiblesInventoryUpdateComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectorScoreComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    static Habbo onlineHabbo(int userId) {
        CollectiblesRuntime runtime = CollectiblesRuntime.installed();
        return runtime == null ? null : runtime.onlineHabbo().apply(userId);
    }

    /** The furni type behind a relic or a collectible, or null. */
    static Item baseItem(int baseItemId) {
        return CollectiblesRuntime.baseItem(baseItemId);
    }

    /** The client names a wallet in most requests; the only valid one is the user's collector wallet. */
    static boolean ownsWallet(Habbo habbo, String wallet) {
        return wallet == null
                || wallet.isEmpty()
                || wallet.equals(
                        CollectiblesService.walletOf(habbo.getHabboInfo().getId()));
    }

    static void sendBalances(Habbo habbo, CollectiblesSettings settings) {
        habbo.getClient()
                .sendResponse(new CollectibleEmeraldBalanceComposer(
                        habbo.getHabboInfo().getCurrencyAmount(settings.emeraldType())));
        habbo.getClient()
                .sendResponse(new CollectibleSilverBalanceComposer(
                        habbo.getHabboInfo().getCurrencyAmount(settings.silverType())));
    }

    static void sendScore(Habbo habbo, CollectiblesService service, CollectiblesSettings settings) throws SQLException {
        CollectorScore score = service.collectorScore(habbo.getHabboInfo().getId(), settings.xpPerLevel());
        habbo.getClient().sendResponse(new CollectorScoreComposer(score.score(), score.highestScore(), score.level()));
    }

    /** Takes a currency through the audited ledger; false when the user cannot pay. */
    static boolean pay(Habbo habbo, int type, int amount, String reason) {
        if (amount <= 0) return true;
        return habbo.tryTakePoints(
                type,
                amount,
                reason,
                EconomyOperationId.create("collectibles:" + habbo.getHabboInfo().getId() + ":" + reason),
                habbo.getHabboInfo().getId());
    }

    static void refund(Habbo habbo, int type, int amount, String reason) {
        if (amount <= 0) return;
        habbo.givePoints(
                type,
                amount,
                reason,
                EconomyOperationId.create("collectibles:" + habbo.getHabboInfo().getId() + ":" + reason),
                habbo.getHabboInfo().getId());
    }

    /**
     * After tokens were minted to an online user: hands out their furni, badge or effect (when
     * {@code hotel.collectibles.deliver_items} is on), pushes them into the collectibles inventory and
     * sends the new collector score.
     */
    static void publishAcquired(
            Habbo habbo, List<Owned> minted, CollectiblesService service, CollectiblesSettings settings)
            throws SQLException {
        if (habbo == null || habbo.getClient() == null || minted.isEmpty()) return;
        Map<String, Product> products = service.products();
        if (settings.deliverItems()) {
            for (Owned token : minted) {
                Product product = products.get(token.productCode());
                if (product != null) deliver(habbo, product);
            }
        }
        habbo.getClient().sendResponse(new CollectiblesInventoryUpdateComposer(minted, List.of(), products));
        sendScore(habbo, service, settings);
    }

    /** Deletes a furni the user gave up (a minted relic's furni, an opened reward box). */
    static void deleteItem(HabboItem item) {
        CollectiblesRuntime runtime = CollectiblesRuntime.installed();
        if (runtime != null && runtime.items() != null) runtime.items().deleteItem(item);
    }

    /** The usable item behind a collectible: its furni (floor, wall, clothing), badge or effect. */
    static void deliver(Habbo habbo, Product product) {
        try {
            switch (product.productType()) {
                case CollectibleData.TYPE_FLOOR, CollectibleData.TYPE_WALL, CollectibleData.TYPE_CLOTHING -> {
                    CollectiblesRuntime runtime = CollectiblesRuntime.installed();
                    ItemManager items = runtime == null ? null : runtime.items();
                    Item base = items == null ? null : items.getItem(Integer.parseInt(product.itemTypeId()));
                    if (base == null) return;
                    HabboItem item = items.createItem(habbo.getHabboInfo().getId(), base, 0, 0, "");
                    if (item == null) return;
                    habbo.getInventory().getItemsComponent().addItem(item);
                    habbo.getClient().sendResponse(new AddHabboItemComposer(item));
                    habbo.getClient().sendResponse(new InventoryRefreshComposer());
                }
                case CollectibleData.TYPE_BADGE -> {
                    if (!product.itemTypeId().isEmpty()) habbo.addBadge(product.itemTypeId());
                }
                case CollectibleData.TYPE_EFFECT -> {
                    int effectId = Integer.parseInt(product.itemTypeId());
                    if (!habbo.getInventory().getEffectsComponent().ownsPermanently(effectId)) {
                        habbo.getInventory().getEffectsComponent().createEffect(effectId, 0);
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
