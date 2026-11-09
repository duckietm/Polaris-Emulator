package polaris.hotel.catalog.marketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.catalog.marketplace.RequestOffersEvent;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceItemPostedComposer;
import polaris.messages.outgoing.collectibles.CollectibleEmeraldBalanceComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.plugin.events.marketplace.MarketPlaceItemSoldEvent;
import polaris.plugin.events.users.UserPointsEvent;

/**
 * AIR's Collectibles marketplace (offer type 5). A collectible is an {@code nft_*} furni; its product code is the
 * furni's class name. It is offered one at a time for emeralds ({@code MarketplaceModel.makeNftOffer}), bought by
 * product code and price ({@code HabboCatalog.buyNftMarketPlaceOffer}), which takes the cheapest open offer of the
 * product, and the seller is paid the price minus the fee in the same transaction as the sale.
 */
public final class NftMarketplace {
    private static final Logger LOGGER = LoggerFactory.getLogger(NftMarketplace.class);

    /** The buy results AIR's {@code MarketPlaceLogic.onNftBuyResult} knows; any other one shows "purchase failed". */
    public static final String RESULT_SUCCESS = "SUCCESS";

    public static final String RESULT_NOT_ENOUGH_EMERALDS = "NOT_ENOUGH_EMERALDS";
    public static final String RESULT_SOLD_OUT = "SOLD_OUT";
    /** Only the buyer's own offers are open: a user never buys from themselves (it would only cost the fee). */
    public static final String RESULT_OWN_OFFER = "OWN_OFFER";

    public static final String RESULT_PRICE_CHANGED = "PRICE_CHANGED";
    public static final String RESULT_DISABLED = "DISABLED";
    public static final String RESULT_FAILED = "FAILED";

    private static final int HISTORY_DAYS = 30;
    private static final int AVERAGE_DAYS = 7;

    private static final String CHEAPEST_OFFER = """
            SELECT m.id, m.item_id, m.user_id, m.price
            FROM marketplace_items m
            INNER JOIN items i ON i.id = m.item_id
            INNER JOIN items_base b ON b.id = i.item_id
            WHERE m.nft = 1 AND m.state = 1 AND m.timestamp > ? AND b.item_name = ? AND m.user_id <> ?
            ORDER BY m.price ASC, m.id ASC
            LIMIT 1
            """;
    private static final String OWN_OPEN_OFFER = """
            SELECT m.id
            FROM marketplace_items m
            INNER JOIN items i ON i.id = m.item_id
            INNER JOIN items_base b ON b.id = i.item_id
            WHERE m.nft = 1 AND m.state = 1 AND m.timestamp > ? AND b.item_name = ? AND m.user_id = ?
            LIMIT 1
            """;
    private static final String OPEN_OFFERS_OF_USER =
            "SELECT COUNT(*) FROM marketplace_items WHERE user_id = ? AND nft = 1 AND state = 1 AND timestamp > ?";
    private static final String OPEN_OFFERS_OF_PRODUCT = """
            SELECT COUNT(*) AS number, MIN(m.price) AS lowest
            FROM marketplace_items m
            INNER JOIN items i ON i.id = m.item_id
            INNER JOIN items_base b ON b.id = i.item_id
            WHERE m.nft = 1 AND m.state = 1 AND m.timestamp > ? AND b.item_name = ?
            """;
    private static final String RECENT_AVERAGE = """
            SELECT FLOOR(AVG(m.price)) AS average
            FROM marketplace_items m
            INNER JOIN items i ON i.id = m.item_id
            INNER JOIN items_base b ON b.id = i.item_id
            WHERE m.nft = 1 AND m.state = 2 AND m.sold_timestamp >= ? AND b.item_name = ?
            """;
    private static final String SALES_HISTORY = """
            SELECT DATEDIFF(CURDATE(), DATE(FROM_UNIXTIME(m.sold_timestamp))) AS day,
                   FLOOR(AVG(m.price)) AS price, COUNT(*) AS sold
            FROM marketplace_items m
            INNER JOIN items i ON i.id = m.item_id
            INNER JOIN items_base b ON b.id = i.item_id
            WHERE m.nft = 1 AND m.state = 2 AND m.sold_timestamp >= ? AND b.item_name = ?
            GROUP BY day
            ORDER BY day DESC
            """;

    private NftMarketplace() {}

    /** The answer to a buy: AIR reads the result, the offer that was bought and its price. */
    public record BuyResult(String result, int offerId, int price) {
        static BuyResult of(String result) {
            return new BuyResult(result, 0, 0);
        }
    }

    /** A day of the sales history: days ago (as a negative offset), the average price, the number sold. */
    public record HistoryDay(int dayOffset, int averagePrice, int sold) {}

    /** The make-offer window's numbers for a product, in emeralds. */
    public record ItemStats(
            int averagePrice,
            int offerCount,
            int historyLength,
            List<HistoryDay> history,
            int lowestCurrentPrice,
            int suggestedPrice) {}

    /**
     * Posts one collectible for sale. Returns a {@link MarketplaceItemPostedComposer} result: posted, a technical
     * error (not a collectible, not owned, wrong kind, price out of range, too many open offers) or disabled.
     */
    public static int makeOffer(GameClient client, int furniType, int itemId, int price) {
        NftMarketplaceRuntime runtime = NftMarketplaceRuntime.installed();
        NftMarketplaceSettings settings = NftMarketplaceSettings.current();
        if (runtime == null || !settings.active()) {
            return MarketplaceItemPostedComposer.MARKETPLACE_DISABLED;
        }

        if (client == null || client.getUser() == null || (furniType != 1 && furniType != 2)) {
            return MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR;
        }

        User user = client.getUser();
        UserItem item = user.getInventory().getItemsComponent().getUserItem(itemId);
        if (item == null
                || !NftMarketplaceSettings.isProduct(item.getBaseItem().getName())
                || (item.getBaseItem().getType().equals(FurnitureType.WALL) ? 2 : 1) != furniType
                || !settings.isValidPrice(price)) {
            return MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR;
        }

        if (openOffersOf(runtime, user.getUserInfo().getId(), settings) >= settings.maxOpenOffers()) {
            return MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR;
        }

        return MarketPlace.postOffer(client, item, price, true)
                ? MarketplaceItemPostedComposer.POST_SUCCESS
                : MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR;
    }

    /**
     * Buys the cheapest open offer of the product, at most at the price the buyer saw. The buyer pays the asking
     * price in emeralds and the seller is paid it minus the fee, together with the sale, or nothing happens.
     */
    public static BuyResult buy(GameClient client, String productCode, int expectedPrice) {
        NftMarketplaceRuntime runtime = NftMarketplaceRuntime.installed();
        NftMarketplaceSettings settings = NftMarketplaceSettings.current();
        if (runtime == null || !settings.active()) return BuyResult.of(RESULT_DISABLED);
        if (client == null || client.getUser() == null) return BuyResult.of(RESULT_FAILED);
        if (!NftMarketplaceSettings.isProduct(productCode) || expectedPrice <= 0) return BuyResult.of(RESULT_FAILED);

        User buyer = client.getUser();
        int buyerId = buyer.getUserInfo().getId();
        RequestOffersEvent.cachedResults.clear();

        try (Connection connection = runtime.dataSource().getConnection()) {
            int offerId;
            int itemId;
            int sellerId;
            int price;
            try (PreparedStatement statement = connection.prepareStatement(CHEAPEST_OFFER)) {
                statement.setInt(1, runtime.clock().getAsInt() - settings.offerSeconds());
                statement.setString(2, productCode);
                statement.setInt(3, buyerId);
                try (ResultSet set = statement.executeQuery()) {
                    if (!set.next()) {
                        return BuyResult.of(
                                hasOwnOpenOffer(connection, runtime, settings, productCode, buyerId)
                                        ? RESULT_OWN_OFFER
                                        : RESULT_SOLD_OUT);
                    }
                    offerId = set.getInt("id");
                    itemId = set.getInt("item_id");
                    sellerId = set.getInt("user_id");
                    price = set.getInt("price");
                }
            }

            if (!settings.isValidPrice(price)) return BuyResult.of(RESULT_FAILED);
            if (price > expectedPrice) return new BuyResult(RESULT_PRICE_CHANGED, offerId, price);
            if (buyer.getUserInfo().getCurrencyAmount(settings.emeraldType()) < price) {
                return new BuyResult(RESULT_NOT_ENOUGH_EMERALDS, offerId, price);
            }

            UserItem item = loadItem(runtime, connection, itemId);
            if (item == null) return BuyResult.of(RESULT_SOLD_OUT);

            User seller = runtime.onlineUser().apply(sellerId);
            MarketPlaceItemSoldEvent sold = new MarketPlaceItemSoldEvent(seller, buyer, item, price);
            if (runtime.plugins().fireEvent(sold).isCancelled() || sold.price != price) {
                return BuyResult.of(RESULT_FAILED);
            }

            // Plugins see the emerald charge as they see every points change, but may not alter it.
            UserPointsEvent charge = new UserPointsEvent(buyer, -price, settings.emeraldType());
            if (runtime.plugins().fireEvent(charge).isCancelled()
                    || charge.points != -price
                    || charge.type != settings.emeraldType()) {
                return BuyResult.of(RESULT_FAILED);
            }

            int payout = settings.payout(price);
            int soldTimestamp = runtime.clock().getAsInt();
            NftMarketplaceSaleTransaction.Sale sale = new NftMarketplaceSaleTransaction.Sale(
                    offerId, itemId, buyerId, sellerId, settings.emeraldType(), price, payout, soldTimestamp);

            NftMarketplaceSaleTransaction.Result committed = seller == null
                    ? LedgerWalletMutation.coordinated(buyer, () -> commit(runtime, sale, buyer, null))
                    : LedgerWalletMutation.coordinated(buyer, seller, () -> commit(runtime, sale, buyer, seller));
            if (committed == null) return BuyResult.of(RESULT_FAILED);

            item.setUserId(buyerId);
            item.needsUpdate(true);
            runtime.async().accept(item);
            buyer.getInventory().getItemsComponent().addItem(item);

            client.sendResponse(new AddUserItemComposer(item));
            client.sendResponse(new InventoryRefreshComposer());
            sendBalance(buyer, settings.emeraldType(), -price);

            if (seller != null) {
                MarketPlaceOffer offer = seller.getInventory().getOffer(offerId);
                if (offer != null) {
                    offer.setState(MarketPlaceState.SOLD);
                    offer.setSoldTimestamp(soldTimestamp);
                }
                if (payout > 0) sendBalance(seller, settings.emeraldType(), payout);
            }

            return new BuyResult(RESULT_SUCCESS, offerId, price);
        } catch (SQLException e) {
            LOGGER.error("Collectibles marketplace purchase of {} failed for user {}", productCode, buyerId, e);
            return BuyResult.of(RESULT_FAILED);
        }
    }

    /** The buyer has an open offer of this product themselves: the one the marketplace list showed them. */
    private static boolean hasOwnOpenOffer(
            Connection connection,
            NftMarketplaceRuntime runtime,
            NftMarketplaceSettings settings,
            String productCode,
            int buyerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(OWN_OPEN_OFFER)) {
            statement.setInt(1, runtime.clock().getAsInt() - settings.offerSeconds());
            statement.setString(2, productCode);
            statement.setInt(3, buyerId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private static NftMarketplaceSaleTransaction.Result commit(
            NftMarketplaceRuntime runtime, NftMarketplaceSaleTransaction.Sale sale, User buyer, User seller) {
        NftMarketplaceSaleTransaction.Result result = NftMarketplaceSaleTransaction.commit(runtime.dataSource(), sale);
        if (result == null) return null;

        LedgerWalletMutation.applyCommitted(
                buyer, sale.currencyType(), result.buyer().balanceAfter());
        if (seller != null && result.seller() != null) {
            LedgerWalletMutation.applyCommitted(
                    seller, sale.currencyType(), result.seller().balanceAfter());
        }
        return result;
    }

    private static void sendBalance(User user, int emeraldType, int delta) {
        if (user.getClient() == null) return;
        int balance = user.getUserInfo().getCurrencyAmount(emeraldType);
        user.getClient().sendResponse(new UserPointsComposer(balance, delta, emeraldType));
        user.getClient().sendResponse(new CollectibleEmeraldBalanceComposer(balance));
    }

    private static UserItem loadItem(NftMarketplaceRuntime runtime, Connection connection, int itemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? runtime.items().loadUserItem(set) : null;
            }
        }
    }

    /** The user's collectible offers that are on sale right now. */
    static int openOffersOf(NftMarketplaceRuntime runtime, int userId, NftMarketplaceSettings settings) {
        try (Connection connection = runtime.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(OPEN_OFFERS_OF_USER)) {
            statement.setInt(1, userId);
            statement.setInt(2, runtime.clock().getAsInt() - settings.offerSeconds());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Could not count the collectible offers of user {}", userId, e);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * The numbers of AIR's collectible make-offer and offer-details windows: the average sale price of the last week,
     * the open offers, a 30 day history oldest day first, the lowest open price and the suggested price.
     */
    public static ItemStats itemStats(String productCode) {
        NftMarketplaceRuntime runtime = NftMarketplaceRuntime.installed();
        NftMarketplaceSettings settings = NftMarketplaceSettings.current();
        if (runtime == null) return new ItemStats(0, 0, HISTORY_DAYS, List.of(), 0, 0);
        int now = runtime.clock().getAsInt();
        int offerCount = 0;
        int lowest = 0;
        int average = 0;
        List<HistoryDay> history = new ArrayList<>();

        try (Connection connection = runtime.dataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(OPEN_OFFERS_OF_PRODUCT)) {
                statement.setInt(1, now - settings.offerSeconds());
                statement.setString(2, productCode);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        offerCount = set.getInt("number");
                        lowest = set.getInt("lowest");
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(RECENT_AVERAGE)) {
                statement.setInt(1, now - AVERAGE_DAYS * 86_400);
                statement.setString(2, productCode);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) average = set.getInt("average");
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(SALES_HISTORY)) {
                statement.setInt(1, now - HISTORY_DAYS * 86_400);
                statement.setString(2, productCode);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        history.add(new HistoryDay(-set.getInt("day"), set.getInt("price"), set.getInt("sold")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not read the collectible stats of {}", productCode, e);
        }

        return new ItemStats(
                average, offerCount, HISTORY_DAYS, history, lowest, MarketPlace.suggestedPrice(average, lowest));
    }

    /** Clearing the sold tab: sold collectibles were paid at the sale, so they are simply dropped from the list. */
    static void forgetSoldOffers(User user) {
        synchronized (user.getInventory()) {
            for (MarketPlaceOffer offer : new ArrayList<>(user.getInventory().getMarketplaceItems())) {
                if (offer != null && offer.isNft() && offer.getState() == MarketPlaceState.SOLD) {
                    user.getInventory().removeMarketplaceOffer(offer);
                }
            }
        }
    }
}
