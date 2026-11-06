package polaris.hotel.catalog.marketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.earnings.EarningsCategory;
import polaris.hotel.earnings.EarningsNotifications;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.WalletBalanceMath;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.catalog.marketplace.RequestOffersEvent;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceBuyErrorComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceCancelSaleComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceItemInfoComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.plugin.events.marketplace.MarketPlaceItemCancelledEvent;
import polaris.plugin.events.marketplace.MarketPlaceItemOfferedEvent;
import polaris.plugin.events.marketplace.MarketPlaceItemSoldEvent;
import polaris.plugin.events.users.UserCreditsEvent;
import polaris.plugin.events.users.UserPointsEvent;

public class MarketPlace {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlace.class);
    private static final String PURCHASE_OFFER_SQL = """
            SELECT item_id, user_id, price, state, timestamp
            FROM marketplace_items
            WHERE id = ? LIMIT 1
            """;
    private static final String PURCHASE_ITEM_SQL = """
            SELECT id, user_id, room_id, item_id, wall_pos, x, y, z, rot,
                   extra_data, wired_data, limited_data, guild_id
            FROM items
            WHERE id = ? LIMIT 1
            """;
    public static final int MINIMUM_LISTING_PRICE = 1;
    public static final int MAXIMUM_LISTING_PRICE = 1_000_000_000;

    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean MARKETPLACE_ENABLED = true;

    // Currency to use.
    public static volatile int MARKETPLACE_CURRENCY = 0;

    public static Set<MarketPlaceOffer> getOwnOffers(User user) {
        Set<MarketPlaceOffer> offers = new HashSet<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT items_base.type AS type, items.item_id AS base_item_id, items.limited_data AS ltd_data, marketplace_items.* FROM marketplace_items INNER JOIN items ON marketplace_items.item_id = items.id INNER JOIN items_base ON items.item_id = items_base.id WHERE marketplace_items.user_id = ? AND marketplace_items.seller_claimed = 0")) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    offers.add(new MarketPlaceOffer(set, true));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return offers;
    }

    public static void takeBackItem(User user, int offerId) {
        MarketPlaceOffer offer = user.getInventory().getOffer(offerId);

        if (offer == null) {
            return;
        }

        if (!Emulator.getPluginManager()
                .fireEvent(new MarketPlaceItemCancelledEvent(offer))
                .isCancelled()) {
            takeBackItem(user, offer);
        }
    }

    /**
     * The AIR 13 "recall all" button (`MarketPlaceLogic.recallAllOffers`): pulls every
     * still-open offer of this player back into their inventory and answers with the ids
     * that were actually recalled, so the client removes exactly those rows.
     */
    public static List<Integer> takeBackAllItems(User user) {
        List<Integer> recalled = new ArrayList<>();

        for (MarketPlaceOffer offer : new ArrayList<>(user.getInventory().getMarketplaceItems())) {
            if (offer == null || offer.getState() != MarketPlaceState.OPEN) {
                continue;
            }

            if (Emulator.getPluginManager()
                    .fireEvent(new MarketPlaceItemCancelledEvent(offer))
                    .isCancelled()) {
                continue;
            }

            takeBackItem(user, offer);

            if (!user.getInventory().getMarketplaceItems().contains(offer)) {
                recalled.add(offer.getOfferId());
            }
        }

        return recalled;
    }

    /**
     * The AIR 13 "clear history" button (`MarketPlaceLogic.clearOwnHistory`): empties one
     * tab of the own-offers window. The official client only ever asks for sold
     * ({@code 2}) or expired ({@code 3}) — `MarketPlaceOfferState.isClearable` — and so
     * does this method, because an open offer is still on sale.
     *
     * <p>A row of ours is never simply forgotten: a sold offer still owes its credits and
     * an expired offer still holds the furni, so clearing a tab settles it first. Sold
     * rows are paid out exactly like the redeem button does, expired rows put the furni
     * back in the inventory. Both leave the tab empty, which is what the button promises,
     * and neither loses anything.
     */
    public static boolean clearOwnHistory(GameClient client, int state) {
        if (client == null || client.getUser() == null) {
            return false;
        }

        User user = client.getUser();

        if (state == MarketPlaceState.SOLD.getState()) {
            getCredits(client);
            return true;
        }

        if (state != MarketPlaceState.CLOSED.getState()) {
            return false;
        }

        for (MarketPlaceOffer offer : new ArrayList<>(user.getInventory().getMarketplaceItems())) {
            if (offer == null || offer.getState() != MarketPlaceState.CLOSED) {
                continue;
            }

            if (Emulator.getPluginManager()
                    .fireEvent(new MarketPlaceItemCancelledEvent(offer))
                    .isCancelled()) {
                continue;
            }

            takeBackItem(user, offer);
        }

        return true;
    }

    private static void takeBackItem(User user, MarketPlaceOffer offer) {
        if (offer != null && user.getInventory().getMarketplaceItems().contains(offer)) {
            RequestOffersEvent.cachedResults.clear();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement ownerCheck = connection.prepareStatement(
                        "SELECT user_id FROM marketplace_items WHERE id = ?",
                        ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY)) {
                    ownerCheck.setInt(1, offer.getOfferId());
                    try (ResultSet ownerSet = ownerCheck.executeQuery()) {
                        ownerSet.last();

                        if (ownerSet.getRow() == 0) {
                            return;
                        }

                        ownerSet.first();
                        if (ownerSet.getInt("user_id") != user.getUserInfo().getId()) {
                            LOGGER.warn(
                                    "User {} attempted to take back marketplace offer {} owned by user {}",
                                    user.getUserInfo().getId(),
                                    offer.getOfferId(),
                                    ownerSet.getInt("user_id"));
                            return;
                        }

                        // The offer row goes and the item comes back together, or neither: a failed item update
                        // used to leave the offer deleted and the item owned by nobody (user_id -1).
                        if (!returnOfferItem(connection, user, offer)) {
                            user.getClient().sendResponse(new MarketplaceCancelSaleComposer(offer, false));
                            return;
                        }

                        user.getInventory().removeMarketplaceOffer(offer);
                        try (PreparedStatement selectItem =
                                connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
                            selectItem.setInt(1, offer.getSoldItemId());
                            try (ResultSet set = selectItem.executeQuery()) {
                                while (set.next()) {
                                    UserItem item = Emulator.getGameEnvironment()
                                            .getItemManager()
                                            .loadUserItem(set);
                                    user.getInventory().getItemsComponent().addItem(item);
                                    user.getClient().sendResponse(new MarketplaceCancelSaleComposer(offer, true));
                                    user.getClient().sendResponse(new AddUserItemComposer(item));
                                    user.getClient().sendResponse(new InventoryRefreshComposer());
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                user.getClient().sendResponse(new MarketplaceCancelSaleComposer(offer, false));
            }
        }
    }

    private static boolean returnOfferItem(Connection connection, User user, MarketPlaceOffer offer)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM marketplace_items WHERE id = ? AND state != 2")) {
                statement.setInt(1, offer.getOfferId());
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
            }

            try (PreparedStatement updateItems =
                    connection.prepareStatement("UPDATE items SET user_id = ? WHERE id = ? LIMIT 1")) {
                updateItems.setInt(1, user.getUserInfo().getId());
                updateItems.setInt(2, offer.getSoldItemId());
                if (updateItems.executeUpdate() != 1) {
                    connection.rollback();
                    LOGGER.warn(
                            "Marketplace offer {} kept: its item {} is missing",
                            offer.getOfferId(),
                            offer.getSoldItemId());
                    return false;
                }
            }

            connection.commit();
            return true;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public static List<MarketPlaceOffer> getOffers(int minPrice, int maxPrice, String search, int sort) {
        return getOffers(minPrice, maxPrice, search, sort, false);
    }

    /**
     * @param combineUniques the AIR 13 `combine_uniques_checkbox` of
     *     `marketplace_search_simple`. When it is on, every serial of the same
     *     limited-edition furni collapses into one row (grouped by base furni only); when
     *     it is off, each serial keeps its own row, which is how the search has always
     *     behaved here.
     */
    public static List<MarketPlaceOffer> getOffers(
            int minPrice, int maxPrice, String search, int sort, boolean combineUniques) {
        List<MarketPlaceOffer> offers = new ArrayList<>(10);
        MarketplaceOffersQuery.Request request =
                new MarketplaceOffersQuery.Request(minPrice, maxPrice, search, sort, combineUniques);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(MarketplaceOffersQuery.sql(request))) {
            MarketplaceOffersQuery.bind(
                    statement, request, Emulator.getIntUnixTimestamp(), MINIMUM_LISTING_PRICE, MAXIMUM_LISTING_PRICE);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    offers.add(new MarketPlaceOffer(set, false));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return offers;
    }

    public static void serializeItemInfo(int itemId, ServerMessage message) {
        serializeItemInfo(MarketplaceItemInfoComposer.CATEGORY_FLOOR, itemId, message);
    }

    /**
     * MarketplaceItemStats: the history rows run oldest day first (the client draws them as one
     * line), and the request's category and sprite id are echoed so the client accepts the answer.
     * AIR's parser reads two more ints after those for its make-offer window: the lowest price an
     * open offer of the furni asks right now (what buyers pay, like the average price) and the
     * suggested asking price (see {@link #suggestedPrice}). Older clients stop reading before them.
     */
    public static void serializeItemInfo(int category, int itemId, ServerMessage message) {
        int recentSalesAverage = averageLastXDays(itemId, 7);
        int[] openOffers = openOffers(itemId);
        int lowestOpenPrice = openOffers[1];

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT avg(marketplace_items.price) as price, COUNT(*) as sold, (datediff(NOW(), DATE(from_unixtime(marketplace_items.timestamp)))) as day FROM marketplace_items INNER JOIN items ON items.id = marketplace_items.item_id INNER JOIN items_base ON items.item_id = items_base.id WHERE items.limited_data = '0:0' AND marketplace_items.state = 2 AND items_base.sprite_id = ? AND DATE(from_unixtime(marketplace_items.timestamp)) >= NOW() - INTERVAL 30 DAY GROUP BY DATE(from_unixtime(marketplace_items.timestamp)) ORDER BY DATE(from_unixtime(marketplace_items.timestamp)) ASC",
                        ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, itemId);

            message.appendInt(calculateCommision(recentSalesAverage));
            message.appendInt(openOffers[0]);
            message.appendInt(30);

            try (ResultSet set = statement.executeQuery()) {
                set.last();
                message.appendInt(set.getRow());
                set.beforeFirst();

                while (set.next()) {
                    message.appendInt(-set.getInt("day"));
                    message.appendInt(MarketPlace.calculateCommision(set.getInt("price")));
                    message.appendInt(set.getInt("sold"));
                }
            }

            message.appendInt(category);
            message.appendInt(itemId);
            message.appendInt(lowestOpenPrice > 0 ? calculateCommision(lowestOpenPrice) : 0);
            message.appendInt(suggestedPrice(recentSalesAverage, lowestOpenPrice));
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static int itemsOnSale(int baseItemId) {
        return openOffers(baseItemId)[0];
    }

    /**
     * The furni's open, unexpired offers: {@code [count, lowest asking price]}, the price without
     * commission and 0 when there is no offer.
     */
    private static int[] openOffers(int baseItemId) {
        int number = 0;
        int lowest = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) as number, MIN(price) as lowest FROM marketplace_items INNER JOIN items ON marketplace_items.item_id = items.id INNER JOIN items_base ON items.item_id = items_base.id WHERE state = 1 AND timestamp >= ? AND items_base.sprite_id = ?",
                        ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, Emulator.getIntUnixTimestamp() - 172800);
            statement.setInt(2, baseItemId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.first()) {
                    number = set.getInt("number");
                    lowest = set.getInt("lowest");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return new int[] {number, lowest};
    }

    /**
     * The asking price AIR's "copy suggested price" button puts into the make-offer window. Here an
     * asking price is what the seller receives (buyers pay the commission on top), so the suggestion
     * is in that unit: the average price of the furni's sales in the last week or, without recent
     * sales, the lowest asking price of its open offers. Zero when there is neither, which hides the
     * suggestion line and the copy button.
     */
    public static int suggestedPrice(int recentSalesAverage, int lowestOpenPrice) {
        if (recentSalesAverage > 0) {
            return recentSalesAverage;
        }

        return Math.max(lowestOpenPrice, 0);
    }

    /** The average sale price (without commission) of the furni over the last {@code days} days; 0 when none. */
    private static int averageLastXDays(int baseItemId, int days) {
        int avg = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT AVG(price) as avg FROM marketplace_items INNER JOIN items ON marketplace_items.item_id = items.id INNER JOIN items_base ON items.item_id = items_base.id WHERE state = 2 AND DATE(from_unixtime(timestamp)) >= NOW() - INTERVAL ? DAY AND items_base.sprite_id = ?",
                        ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, days);
            statement.setInt(2, baseItemId);

            try (ResultSet set = statement.executeQuery()) {
                set.first();
                avg = set.getInt("avg");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return avg;
    }

    public static void buyItem(int offerId, GameClient client) {
        RequestOffersEvent.cachedResults.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(PURCHASE_OFFER_SQL)) {
                statement.setInt(1, offerId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        try (PreparedStatement itemStatement = connection.prepareStatement(
                                PURCHASE_ITEM_SQL, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                            itemStatement.setInt(1, set.getInt("item_id"));
                            try (ResultSet itemSet = itemStatement.executeQuery()) {
                                itemSet.first();

                                if (itemSet.getRow() > 0) {
                                    int rawPrice = set.getInt("price");
                                    if (!isValidListingPrice(rawPrice)) {
                                        sendErrorMessage(client, set.getInt("item_id"), offerId);
                                        return;
                                    }

                                    int price = MarketPlace.calculateCommision(rawPrice);
                                    // An offer past the listing window no longer appears in
                                    // the marketplace and its furni can be redeemed by the
                                    // seller, but it stays state = 1: buying one by id used
                                    // to work. An offer of the buyer's own was never
                                    // refused either, which moved credits to themselves
                                    // minus the commission.
                                    boolean expired = set.getInt("timestamp")
                                            < Emulator.getIntUnixTimestamp()
                                                    - MarketplaceOffersQuery.LISTING_WINDOW_SECONDS;
                                    boolean ownOffer = set.getInt("user_id")
                                            == client.getUser().getUserInfo().getId();
                                    if (set.getInt("state") != 1 || expired || ownOffer) {
                                        sendErrorMessage(client, set.getInt("item_id"), offerId);
                                    } else if ((MARKETPLACE_CURRENCY == 0
                                                    && price
                                                            > client.getUser()
                                                                    .getUserInfo()
                                                                    .getCredits())
                                            || (MARKETPLACE_CURRENCY > 0
                                                    && price
                                                            > client.getUser()
                                                                    .getUserInfo()
                                                                    .getCurrencyAmount(MARKETPLACE_CURRENCY))) {
                                        client.sendResponse(new MarketplaceBuyErrorComposer(
                                                MarketplaceBuyErrorComposer.NOT_ENOUGH_CREDITS, 0, offerId, price));
                                    } else {
                                        User user = Emulator.getGameServer()
                                                .getGameClientManager()
                                                .getUser(set.getInt("user_id"));
                                        UserItem item = Emulator.getGameEnvironment()
                                                .getItemManager()
                                                .loadUserItem(itemSet);

                                        MarketPlaceItemSoldEvent event = new MarketPlaceItemSoldEvent(
                                                user, client.getUser(), item, set.getInt("price"));
                                        if (Emulator.getPluginManager()
                                                        .fireEvent(event)
                                                        .isCancelled()
                                                || !isValidListingPrice(event.price)) {
                                            return;
                                        }

                                        int soldTimestamp = Emulator.getIntUnixTimestamp();
                                        event.price = calculateCommision(event.price);

                                        PreparedCharge charge = prepareMarketplaceCharge(client.getUser(), event.price);
                                        if (charge == null) return;

                                        EconomyMutationResult walletMutation =
                                                LedgerWalletMutation.coordinated(client.getUser(), () -> {
                                                    EconomyMutationResult mutation =
                                                            MarketPlacePurchaseTransaction.commit(
                                                                    offerId,
                                                                    item.getId(),
                                                                    client.getUser()
                                                                            .getUserInfo()
                                                                            .getId(),
                                                                    charge.currencyType(),
                                                                    -charge.delta(),
                                                                    soldTimestamp);
                                                    if (mutation != null) {
                                                        LedgerWalletMutation.applyCommitted(
                                                                client.getUser(),
                                                                charge.currencyType(),
                                                                mutation.balanceAfter());
                                                    }
                                                    return mutation;
                                                });
                                        if (walletMutation == null) {
                                            sendErrorMessage(
                                                    client, item.getBaseItem().getId(), offerId);
                                            return;
                                        }

                                        item.setUserId(
                                                client.getUser().getUserInfo().getId());
                                        item.needsUpdate(true);
                                        Emulator.getThreading().run(item);

                                        client.getUser()
                                                .getInventory()
                                                .getItemsComponent()
                                                .addItem(item);

                                        publishMarketplaceCharge(client, charge);

                                        client.sendResponse(new AddUserItemComposer(item));
                                        client.sendResponse(new InventoryRefreshComposer());
                                        client.sendResponse(new MarketplaceBuyErrorComposer(
                                                MarketplaceBuyErrorComposer.REFRESH, 0, offerId, price));

                                        if (user != null) {
                                            MarketPlaceOffer offer =
                                                    user.getInventory().getOffer(offerId);
                                            if (offer != null) {
                                                offer.setState(MarketPlaceState.SOLD);
                                                offer.setSoldTimestamp(soldTimestamp);
                                                // AIR: the sale waits in the seller's earnings window.
                                                EarningsNotifications.newReward(user, EarningsCategory.MARKETPLACE);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void sendErrorMessage(GameClient client, int baseItemId, int offerId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT marketplace_items.*, COUNT( * ) AS count\n" + "FROM marketplace_items\n"
                                + "INNER JOIN items ON marketplace_items.item_id = items.id\n"
                                + "INNER JOIN items_base ON items.item_id = items_base.id\n"
                                + "WHERE items_base.sprite_id = ( \n"
                                + "SELECT items_base.sprite_id\n"
                                + "FROM items_base\n"
                                + "WHERE items_base.id = ? LIMIT 1)\n"
                                + "ORDER BY price ASC\n"
                                + "LIMIT 1",
                        ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, baseItemId);
            try (ResultSet countSet = statement.executeQuery()) {
                countSet.last();
                if (countSet.getRow() == 0)
                    client.sendResponse(
                            new MarketplaceBuyErrorComposer(MarketplaceBuyErrorComposer.SOLD_OUT, 0, offerId, 0));
                else {
                    countSet.first();
                    client.sendResponse(new MarketplaceBuyErrorComposer(
                            MarketplaceBuyErrorComposer.UPDATES,
                            countSet.getInt("count"),
                            countSet.getInt("id"),
                            MarketPlace.calculateCommision(countSet.getInt("price"))));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static boolean sellItem(GameClient client, UserItem item, int price) {
        if (item == null || client == null) return false;

        // A rented item expires back to the hotel: listing it sells a loan.
        if (!item.getBaseItem().allowMarketplace() || item.hasRentPeriod() || !isValidListingPrice(price)) {
            return false;
        }

        MarketPlaceItemOfferedEvent event = new MarketPlaceItemOfferedEvent(client.getUser(), item, price);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return false;
        }

        RequestOffersEvent.cachedResults.clear();

        // The client is told the item left only once the offer is saved: a failed insert kept the item on the server
        // while the client showed it gone until the next refresh.
        int clientItemId = event.item.getGiftAdjustedId();
        boolean fromGift = event.item.isFromGift();

        event.item.setFromGift(false);

        MarketPlaceOffer offer = new MarketPlaceOffer(event.item, event.price, client.getUser());
        if (!offer.isPersisted()) {
            event.item.setFromGift(fromGift);
            LOGGER.warn(
                    "Marketplace offer insert failed for user {} item {}",
                    client.getUser().getUserInfo().getId(),
                    event.item.getId());
            return false;
        }

        client.sendResponse(new RemoveUserItemComposer(clientItemId));
        client.sendResponse(new InventoryRefreshComposer());

        client.getUser().getInventory().addMarketplaceOffer(offer);
        client.getUser().getInventory().getItemsComponent().removeUserItem(event.item);
        item.setUserId(-1);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        return true;
    }

    public static void getCredits(GameClient client) {
        claimSoldCredits(client);
    }

    /**
     * Pays the seller what their sold offers earned and returns the amount paid (0 when nothing was paid).
     */
    public static int claimSoldCredits(GameClient client) {
        Set<MarketPlaceOffer> offers = new HashSet<>();
        offers.addAll(client.getUser().getInventory().getMarketplaceItems());

        long claimable = 0;
        for (MarketPlaceOffer offer : offers) {
            if (offer.getState().equals(MarketPlaceState.SOLD)) {
                if (offer.getPrice() < 0) {
                    LOGGER.warn("Rejected negative marketplace payout for offer {}", offer.getOfferId());
                    return 0;
                }
                claimable += offer.getPrice();
            }
        }

        int currentBalance = MARKETPLACE_CURRENCY == 0
                ? client.getUser().getUserInfo().getCredits()
                : client.getUser().getUserInfo().getCurrencyAmount(MARKETPLACE_CURRENCY);
        if (claimable > Integer.MAX_VALUE) {
            LOGGER.warn(
                    "Marketplace payout for user {} exceeds wallet range: {}",
                    client.getUser().getUserInfo().getId(),
                    claimable);
            return 0;
        }
        try {
            WalletBalanceMath.checkedBalance(currentBalance, (int) claimable);
        } catch (IllegalArgumentException e) {
            LOGGER.warn(
                    "Marketplace payout for user {} would overflow wallet: current={}, payout={}",
                    client.getUser().getUserInfo().getId(),
                    currentBalance,
                    claimable);
            return 0;
        }

        int sellerId = client.getUser().getUserInfo().getId();
        List<MarketPlaceOffer> claimed = new ArrayList<>();
        List<Integer> offerIds = new ArrayList<>();
        int credits = 0;
        for (MarketPlaceOffer offer : offers) {
            if (offer.getState().equals(MarketPlaceState.SOLD)) {
                credits = WalletBalanceMath.checkedBalance(credits, offer.getPrice());
                claimed.add(offer);
                offerIds.add(offer.getOfferId());
            }
        }
        offers.clear();

        if (claimed.isEmpty() || credits == 0) {
            return 0;
        }

        // A plugin can refuse the payout, but not change it: the amount is what the offers earned.
        PreparedCharge payout = prepareMarketplaceMutation(client.getUser(), credits);
        if (payout == null) return 0;

        // The offers are flagged as paid out and the wallet credited together, or neither happens and the offers stay
        // claimable; the in-memory offers only leave once the transaction committed.
        EconomyMutationResult mutation = LedgerWalletMutation.coordinated(client.getUser(), () -> {
            EconomyMutationResult committed;
            try (Connection connection = openConnection()) {
                committed = MarketPlacePayoutTransaction.commit(
                        connection, sellerId, offerIds, payout.currencyType(), payout.delta());
            } catch (SQLException e) {
                LOGGER.error("Marketplace payout could not reach the database for user {}", sellerId, e);
                return null;
            }
            if (committed != null) {
                LedgerWalletMutation.applyCommitted(client.getUser(), payout.currencyType(), committed.balanceAfter());
            }
            return committed;
        });
        if (mutation == null) {
            LOGGER.warn("Marketplace payout of {} not made for user {}; offers stay claimable", credits, sellerId);
            return 0;
        }

        synchronized (client.getUser().getInventory()) {
            for (MarketPlaceOffer offer : claimed) {
                client.getUser().getInventory().removeMarketplaceOffer(offer);
            }
        }
        publishMarketplaceCharge(client, payout);

        for (MarketPlaceOffer offer : claimed) {
            offer.needsUpdate(true);
            Emulator.getThreading().run(offer);
        }

        return credits;
    }

    /** The payout's way to the database. */
    private static Connection openConnection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    public static int calculateCommision(int price) {
        long commission = price + (long) Math.ceil(price / 100.0);
        return commission > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) commission;
    }

    private static PreparedCharge prepareMarketplaceCharge(User buyer, int price) {
        return prepareMarketplaceMutation(buyer, -price);
    }

    /** Asks the plugins about a marketplace wallet change; null when one refused it or tried to change it. */
    private static PreparedCharge prepareMarketplaceMutation(User user, int delta) {
        if (MARKETPLACE_CURRENCY == 0) {
            UserCreditsEvent event = new UserCreditsEvent(user, delta);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled() || event.credits != delta) return null;
            return new PreparedCharge(-1, event.credits);
        }

        UserPointsEvent event = new UserPointsEvent(user, delta, MARKETPLACE_CURRENCY);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()
                || event.type != MARKETPLACE_CURRENCY
                || event.points != delta) return null;
        return new PreparedCharge(event.type, event.points);
    }

    private static void publishMarketplaceCharge(GameClient client, PreparedCharge charge) {
        if (charge.currencyType() < 0) {
            client.sendResponse(new UserCreditsComposer(client.getUser()));
            return;
        }

        client.sendResponse(new UserPointsComposer(
                client.getUser().getUserInfo().getCurrencyAmount(charge.currencyType()),
                charge.delta(),
                charge.currencyType()));
    }

    private record PreparedCharge(int currencyType, int delta) {}

    public static boolean isValidListingPrice(int price) {
        return price >= MINIMUM_LISTING_PRICE && price <= MAXIMUM_LISTING_PRICE;
    }
}
