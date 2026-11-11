package polaris.messages.incoming.catalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogLimitedConfiguration;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageAccessPolicy;
import polaris.hotel.catalog.CatalogPaymentService;
import polaris.hotel.catalog.CatalogPurchaseMath;
import polaris.hotel.catalog.ClubOffer;
import polaris.hotel.catalog.LtdRaffleManager;
import polaris.hotel.catalog.layouts.BuildersClubAddonsLayout;
import polaris.hotel.catalog.layouts.BuildersClubFrontPageLayout;
import polaris.hotel.catalog.layouts.BuildersClubLoyaltyLayout;
import polaris.hotel.catalog.layouts.ClubBuyLayout;
import polaris.hotel.catalog.layouts.VipBuyLayout;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionBadgeDisplay;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.InteractionGuildFurni;
import polaris.hotel.items.interactions.InteractionGuildGate;
import polaris.hotel.items.interactions.InteractionHopper;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTrophy;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;
import polaris.messages.outgoing.catalog.GiftReceiverNotFoundComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;
import polaris.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.users.UserClubComposer;
import polaris.threading.runnables.ShutdownEmulator;

public class CatalogBuyItemAsGiftEvent extends MessageHandler {
    static final String GIFT_HIDE_SENDER = "acc_gift_hide_sender";

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogBuyItemAsGiftEvent.class);

    private static final int USERNAME_MAX = 32;
    private static final int EXTRADATA_MAX = 256;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public String getRatelimitGroup() {
        return "catalog.purchase";
    }

    /** One gift request as the packet carries it, the message already cut and filtered. */
    private record GiftRequest(
            int pageId,
            int itemId,
            String extraData,
            String username,
            String message,
            int spriteId,
            int color,
            int ribbonId,
            boolean showName) {}

    /**
     * What a gift has reserved so far, so a gift that is not committed can give it back: the limited number, and the
     * items written in its transaction, which the box lists.
     */
    private static final class GiftAttempt {
        private CatalogLimitedConfiguration limitedConfiguration;
        private int limitedItemId;
        private int limitedNumber;
        private int limitedStack;
        private final Set<UserItem> createdItems = new HashSet<>();

        private boolean committed;
    }

    @Override
    public void handle() throws Exception {
        if (Emulator.getIntUnixTimestamp() - this.client.getUser().getUserStats().lastGiftTimestamp
                < CatalogManager.PURCHASE_COOLDOWN) {
            LOGGER.debug("cooldown blocked purchase");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (ShutdownEmulator.timestamp > 0) {
            LOGGER.debug("emulator closing");
            this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                    (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return;
        }

        synchronized (this.client.getUser().getUserStats()) {
            if (this.client.getUser().getUserStats().isPurchasingFurniture) {
                LOGGER.debug("isPurchasingFurniture already true");
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return;
            }
            this.client.getUser().getUserStats().isPurchasingFurniture = true;
        }

        GiftAttempt attempt = new GiftAttempt();

        try {
            this.sendGift(attempt);
        } catch (Exception e) {
            LOGGER.error("Exception caught", e);
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
        } finally {
            try {
                this.undo(attempt);
            } finally {
                synchronized (this.client.getUser().getUserStats()) {
                    this.client.getUser().getUserStats().isPurchasingFurniture = false;
                }
            }
        }
    }

    private GiftRequest readRequest() {
        int pageId = this.packet.readInt();
        int itemId = this.packet.readInt();
        String extraData = this.packet.readString();
        if (extraData.length() > EXTRADATA_MAX) extraData = extraData.substring(0, EXTRADATA_MAX);
        String username = this.packet.readString();
        if (username.length() > USERNAME_MAX) username = username.substring(0, USERNAME_MAX);
        int messageMax = Emulator.getConfig().getInt("hotel.gifts.length.max", 300);
        String rawMessage = this.packet.readString();
        if (rawMessage.length() > messageMax) rawMessage = rawMessage.substring(0, messageMax);
        String message = Emulator.getGameEnvironment().getWordFilter().filter(rawMessage, this.client.getUser());
        int spriteId = this.packet.readInt();
        int color = this.packet.readInt();
        int ribbonId = this.packet.readInt();
        boolean showName = this.packet.readBoolean();

        // Only staff may hide who sent a gift; the client offers it to them alone.
        if (!showName && !this.client.getUser().hasPermission(GIFT_HIDE_SENDER)) showName = true;

        return new GiftRequest(pageId, itemId, extraData, username, message, spriteId, color, ribbonId, showName);
    }

    /** Reads the request and sends either a club offer or a wrapped item. */
    private void sendGift(GiftAttempt attempt) throws Exception {
        GiftRequest request = this.readRequest();

        LOGGER.debug(
                "Gift request: pageId={}, itemId={}, spriteId={}, color={}, ribbonId={}",
                request.pageId(),
                request.itemId(),
                request.spriteId(),
                request.color(),
                request.ribbonId());

        CatalogPage clubGiftPage =
                Emulator.getGameEnvironment().getCatalogManager().catalogPages.get(request.pageId());
        if (this.isClubOfferPage(clubGiftPage)) {
            if (!this.canAccessPage(clubGiftPage)) {
                this.client.sendResponse(
                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return;
            }
            this.handleClubOfferGift(clubGiftPage, request.itemId(), request.username());
            return;
        }

        CatalogManager.GiftWrappingSnapshot giftWrapping =
                Emulator.getGameEnvironment().getCatalogManager().getGiftWrappingSnapshot();
        Map<Integer, Integer> giftWrappers = giftWrapping.wrappers();
        Map<Integer, Integer> giftFurniture = giftWrapping.furniture();

        Item giftItem = this.resolveGiftWrapItem(
                request.spriteId(), request.color(), request.ribbonId(), giftWrappers, giftFurniture);
        if (giftItem == null) return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.sendItemGift(connection, request, giftItem, giftWrappers.containsKey(request.spriteId()), attempt);
        }
    }

    /** Checks, charges, creates and delivers a wrapped item; every refusal is answered before it returns. */
    private void sendItemGift(
            Connection connection, GiftRequest request, Item giftItem, boolean paidWrap, GiftAttempt attempt)
            throws Exception {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(request.username());

        int userId = this.resolveRecipientId(connection, user, request.username());

        if (userId == 0) {
            LOGGER.debug("receiver not found -> {}", request.username());
            this.client.sendResponse(new GiftReceiverNotFoundComposer());
            return;
        }

        CatalogItem item = this.findGiftableItem(request.pageId(), request.itemId());
        if (item == null) return;

        GiftPrice price = this.priceGift(item, request.itemId(), paidWrap);
        if (price == null) return;

        // With the LTD raffle on, a limited offer is collected and drawn rather than sold
        // to whoever clicks first - but this path reserved a number directly, so buying it
        // as a gift, to an alt or to oneself, skipped the draw and took a copy at once.
        if (item.isLimited() && LtdRaffleManager.isEnabled()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (item.isLimited() && !this.reserveLimitedNumber(item, request.itemId(), attempt)) return;

        boolean badgeFound = this.recipientHasBadge(connection, user, userId, item);

        if (badgeFound) {
            LOGGER.debug("receiver already has badge");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.ALREADY_HAVE_BADGE));
            return;
        }

        if (this.rejectsUnsupportedGift(item)) return;

        GiftDelivery delivery = LedgerWalletMutation.coordinated(
                this.client.getUser(),
                () -> this.persistGift(connection, request, item, price, giftItem, user, userId, badgeFound, attempt));
        if (delivery == null) return;

        // Committed: the payment, the contents, the box and the limited sale are in the database together, so
        // from here nothing is undone. The gift used to be charged, filled, wrapped and sold in separate steps: a
        // crash between them left the sender charged with no gift, or rows nobody owned.
        attempt.committed = true;
        CatalogPaymentService.publishTaken(this.client.getUser(), delivery.payment());

        int now = Emulator.getIntUnixTimestamp();
        this.client.getUser().getUserStats().lastGiftTimestamp = now;
        if (item.isLimited()) {
            this.client.getUser().getUserStats().addLtdLog(item.getId(), now);
        }

        if (this.client.getUser().getUserInfo().getId() != userId) {
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GiftGiver"));
        }

        this.deliverToRecipient(user, delivery.gift(), request.showName());

        if (this.client.getUser().getUserInfo().getId() != userId) {
            AchievementManager.progressAchievement(
                    userId,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GiftReceiver"));
        }

        this.client.sendResponse(new PurchaseOKComposer(item));
    }

    /** The box written in the gift's transaction, and the payment to apply to the wallet once it is committed. */
    private record GiftDelivery(UserItem gift, CatalogPaymentService.TakenPayment payment) {}

    /**
     * Writes the payment, the contents, the box and the limited sale in one transaction on {@code connection}; null
     * once the sender has been answered, with nothing written. Runs under the sender's wallet lock.
     */
    private GiftDelivery persistGift(
            Connection connection,
            GiftRequest request,
            CatalogItem item,
            GiftPrice price,
            Item giftItem,
            User user,
            int userId,
            boolean badgeFound,
            GiftAttempt attempt)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        boolean committed = false;
        connection.setAutoCommit(false);
        try {
            CatalogPaymentService.TakenPayment payment = this.charge(connection, item, price);
            if (payment == null) return null;

            if (!this.createGiftContents(
                    item,
                    user,
                    userId,
                    connection,
                    request.extraData(),
                    badgeFound,
                    attempt.limitedStack,
                    attempt.limitedNumber,
                    attempt.createdItems)) {
                return null;
            }

            String giftData = this.buildGiftData(
                    attempt.createdItems, request.color(), request.ribbonId(), request.showName(), request.message());
            if (giftData == null) return null;

            UserItem gift = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(connection, userId, giftItem, 0, 0, giftData);
            if (gift == null) {
                LOGGER.debug("gift box creation failed");
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return null;
            }

            this.recordLimitedSale(connection, item, attempt);

            connection.commit();
            committed = true;
            return new GiftDelivery(gift, payment);
        } finally {
            if (!committed) {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    LOGGER.error("Gift could not be rolled back", e);
                }
            }
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Takes a number of a limited item for the gift, creating its limited configuration when it has none yet. False
     * once the sender has been told the item is sold out or over a daily limit.
     */
    private boolean reserveLimitedNumber(CatalogItem item, int itemId, GiftAttempt attempt) {
        // A limited item without a configuration yet gets one below; asking the missing one
        // for its stock used to end the gift in a NullPointerException.
        CatalogLimitedConfiguration limitedConfiguration =
                Emulator.getGameEnvironment().getCatalogManager().getLimitedConfig(item);

        if (limitedConfiguration != null && limitedConfiguration.available() == 0) {
            LOGGER.debug("LTD available=0 -> itemId={}", itemId);
            this.client.sendResponse(new AlertLimitedSoldOutComposer());
            return false;
        }

        if (this.exceedsDailyLimitedLimits(item)) return false;

        if (limitedConfiguration == null) {
            limitedConfiguration =
                    Emulator.getGameEnvironment().getCatalogManager().createOrUpdateLimitedConfig(item);
        }

        // The stock was counted above, but another buyer can take the last number before it is reserved here.
        OptionalInt reservation = limitedConfiguration.pollNumber();
        if (reservation.isEmpty()) {
            LOGGER.debug("LTD number taken meanwhile -> itemId={}", itemId);
            this.client.sendResponse(new AlertLimitedSoldOutComposer());
            return false;
        }

        attempt.limitedNumber = reservation.getAsInt();
        attempt.limitedConfiguration = limitedConfiguration;
        attempt.limitedItemId = item.getId();
        attempt.limitedStack = limitedConfiguration.getTotalSet();
        return true;
    }

    /**
     * Takes the price from the sender in the gift's transaction, nothing of a currency they have without limit; null
     * once the sender has been told they cannot pay.
     */
    private CatalogPaymentService.TakenPayment charge(Connection connection, CatalogItem item, GiftPrice price)
            throws SQLException {
        int chargeCredits = this.client.getUser().hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : price.credits();
        boolean hasInfinitePoints = item.getPointsType() == 0
                ? this.client.getUser().hasPermission(Permission.ACC_INFINITE_PIXELS)
                : this.client.getUser().hasPermission(Permission.ACC_INFINITE_POINTS);
        int chargePoints = hasInfinitePoints ? 0 : price.points();
        try {
            return CatalogPaymentService.takeWithin(
                    connection, this.client.getUser(), chargeCredits, item.getPointsType(), chargePoints);
        } catch (IllegalArgumentException e) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }
    }

    /**
     * One number, one sale, as a purchase for oneself records it: both halves of a teleporter carry the same number,
     * and the box carries none. The first item created is the one sold.
     */
    private void recordLimitedSale(Connection connection, CatalogItem item, GiftAttempt attempt) throws SQLException {
        if (attempt.limitedConfiguration == null) return;

        UserItem limitedItem = null;
        for (UserItem itm : attempt.createdItems) {
            if (itm == null) {
                // Trip the rollback so nothing is charged or created.
                throw new IllegalStateException("null item before limitedSold()");
            }
            if (limitedItem == null || itm.getId() < limitedItem.getId()) limitedItem = itm;
        }
        if (limitedItem == null) return;

        // Written in the gift's transaction: a failure rolls the whole gift back, and once committed the number is
        // sold and not given back.
        attempt.limitedConfiguration.limitedSold(connection, item.getId(), this.client.getUser(), limitedItem);
    }

    /**
     * Returns the limited number a gift reserved when it was not committed. Everything else it wrote was rolled back
     * with its transaction, so there is nothing to delete or refund.
     */
    private void undo(GiftAttempt attempt) {
        if (attempt.committed) return;

        if (attempt.limitedConfiguration != null && attempt.limitedNumber > 0) {
            attempt.limitedConfiguration.restoreNumber(attempt.limitedItemId, attempt.limitedNumber);
        }
    }

    /** The furni the gift is wrapped in, checked against the wrapping on offer; null once the sender has been answered. */
    private Item resolveGiftWrapItem(
            int spriteId,
            int color,
            int ribbonId,
            Map<Integer, Integer> giftWrappers,
            Map<Integer, Integer> giftFurniture) {
        if (!giftWrappers.containsKey(spriteId) && !giftFurniture.containsKey(spriteId)) {
            LOGGER.debug("invalid spriteId for gift wrapper/furni -> {}", spriteId);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return null;
        }

        if (!GiftConfigurationComposer.BOX_TYPES.contains(color)
                || !GiftConfigurationComposer.RIBBON_TYPES.contains(ribbonId)) {
            LOGGER.debug("invalid color/ribbon -> color={}, ribbonId={}", color, ribbonId);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return null;
        }

        Integer iItemId = giftWrappers.get(spriteId);

        if (iItemId == null) {
            iItemId = giftFurniture.get(spriteId);
        }

        if (iItemId == null) {
            LOGGER.debug("iItemId null for spriteId={}", spriteId);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return null;
        }

        Item giftItem = Emulator.getGameEnvironment().getItemManager().getItem(iItemId);

        if (giftItem == null) {
            LOGGER.debug("direct giftItem null, trying random fallback. iItemId={}", iItemId);
            Object[] giftFurnitureIds = giftFurniture.values().toArray();
            if (giftFurnitureIds.length > 0) {
                giftItem = Emulator.getGameEnvironment().getItemManager().getItem((Integer)
                        giftFurnitureIds[Emulator.getRandom().nextInt(giftFurnitureIds.length)]);
            }

            if (giftItem == null) {
                LOGGER.debug("fallback giftItem also null");
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return null;
            }
        }

        return giftItem;
    }

    /** The id of the recipient, online or in the database; 0 when nobody has that name. */
    private int resolveRecipientId(Connection connection, User user, String username) {
        int userId = 0;

        if (user == null) {
            LOGGER.debug("target user not online, checking DB -> {}", username);
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                statement.setString(1, username);

                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        userId = set.getInt(1);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        } else {
            userId = user.getUserInfo().getId();
        }

        return userId;
    }

    /** The catalog item asked for, when its page is open to the sender and it may be given; null once answered. */
    private CatalogItem findGiftableItem(int pageId, int itemId) {
        CatalogPage page =
                Emulator.getGameEnvironment().getCatalogManager().catalogPages.get(pageId);

        if (page == null) {
            LOGGER.debug("page null -> {}", pageId);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return null;
        }

        if (page.getRank() > this.client.getUser().getUserInfo().getRank().getId()
                || !page.isEnabled()
                || !page.isVisible()) {
            LOGGER.debug(
                    "page access denied. pageRank={}, userRank={}, enabled={}, visible={}",
                    page.getRank(),
                    this.client.getUser().getUserInfo().getRank().getId(),
                    page.isEnabled(),
                    page.isVisible());
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }

        CatalogItem item = page.getCatalogItem(itemId);

        // Search-results gift sends the catalog offer_id as
        // itemId, not catalog_items.id - see the same fix in
        // CatalogBuyItemEvent. Fall back to scanning the
        // page for the matching offer_id.
        if (item == null) {
            for (CatalogItem candidate : page.getCatalogItems().values()) {
                if (candidate != null && candidate.getOfferId() == itemId) {
                    item = candidate;
                    break;
                }
            }
        }

        if (item == null) {
            LOGGER.debug("catalog item null -> {}", itemId);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return null;
        }

        if (item.getHabbiconId() > 0) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }

        // A rented furni is given with no end date by the gift path, so a rent offer is not a gift.
        if (item.isRentOffer()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }

        if (item.isClubOnly() && !this.client.getUser().getUserStats().hasActiveClub()) {
            LOGGER.debug("item requires club -> itemId={}", itemId);
            this.client.sendResponse(
                    new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.REQUIRES_CLUB));
            return null;
        }

        for (Item baseItem : item.getBaseItems()) {
            if (!baseItem.allowGift()) {
                LOGGER.debug("base item not giftable -> baseItemId={}, name={}", baseItem.getId(), baseItem.getName());
                this.client.sendResponse(
                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return null;
            }
        }

        if (item.isLimited()) {
            if (item.getLimitedStack() == item.getLimitedSells()) {
                LOGGER.debug("LTD sold out -> itemId={}", itemId);
                this.client.sendResponse(new AlertLimitedSoldOutComposer());
                return null;
            }
        }

        return item;
    }

    private record GiftPrice(int credits, int points) {}

    /** What the gift costs with its wrapping, when the sender can pay it; null once the sender has been answered. */
    private GiftPrice priceGift(CatalogItem item, int itemId, boolean isPaidWrap) {
        // Paid wrapping (giftWrappers) costs hotel.gifts.special.price; default furni wrap is free.
        int wrapFee = isPaidWrap ? Emulator.getConfig().getInt("hotel.gifts.special.price", 0) : 0;
        int totalCredits;
        int totalPoints;
        try {
            totalCredits = CatalogPurchaseMath.checkedAdd(item.getCredits(), wrapFee);
            totalPoints = CatalogPurchaseMath.requireNonNegative(item.getPoints(), "gift points price");
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Rejected invalid gift price for itemId={}", itemId);
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }

        if (totalCredits > this.client.getUser().getUserInfo().getCredits()
                || totalPoints > this.client.getUser().getUserInfo().getCurrencyAmount(item.getPointsType())) {
            LOGGER.debug(
                    "not enough currency. creditsNeeded={}, pointsNeeded={}, pointsType={}",
                    totalCredits,
                    totalPoints,
                    item.getPointsType());
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }

        return new GiftPrice(totalCredits, totalPoints);
    }

    /** Whether the sender reached a daily limit on limited items, when those limits are on; they are told so. */
    private boolean exceedsDailyLimitedLimits(CatalogItem item) {
        if (Emulator.getConfig().getBoolean("hotel.catalog.ltd.limit.enabled")) {
            int ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.total");
            if (this.client.getUser().getUserStats().totalLtds() >= ltdLimit) {
                LOGGER.debug("sender reached daily total LTD limit");
                this.client
                        .getUser()
                        .alert(Emulator.getTexts()
                                .getValue("error.catalog.buy.limited.daily.total")
                                .replace(
                                        "%itemname%",
                                        item.getBaseItems().iterator().next().getDisplayName())
                                .replace("%limit%", ltdLimit + ""));
                return true;
            }

            ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.item");
            if (this.client.getUser().getUserStats().totalLtds(item.getId()) >= ltdLimit) {
                LOGGER.debug("sender reached daily LTD item limit");
                this.client
                        .getUser()
                        .alert(Emulator.getTexts()
                                .getValue("error.catalog.buy.limited.daily.item")
                                .replace(
                                        "%itemname%",
                                        item.getBaseItems().iterator().next().getDisplayName())
                                .replace("%limit%", ltdLimit + ""));
                return true;
            }
        }

        return false;
    }

    /** Whether the recipient already owns a badge the item would give, online or in the database. */
    private boolean recipientHasBadge(Connection connection, User user, int userId, CatalogItem item)
            throws SQLException {
        boolean badgeFound = false;
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem.getType() == FurnitureType.BADGE) {
                if (user != null) {
                    if (user.getInventory().getBadgesComponent().hasBadge(baseItem.getName())) {
                        badgeFound = true;
                    }
                } else {
                    int c = 0;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*) as c FROM users_badges WHERE user_id = ? AND badge_code LIKE ?")) {
                        statement.setInt(1, userId);
                        statement.setString(2, baseItem.getName());

                        try (ResultSet rSet = statement.executeQuery()) {
                            if (rSet.next()) {
                                c = rSet.getInt("c");
                            }
                        }
                    }

                    if (c != 0) {
                        badgeFound = true;
                    }
                }
            }
        }

        return badgeFound;
    }

    /** Whether the item has a shape a gift cannot hold; the sender is answered before any row is created. */
    private boolean rejectsUnsupportedGift(CatalogItem item) {
        if (item.getAmount() > 1 || item.getBaseItems().size() > 1) {
            LOGGER.debug(
                    "unsupported multi amount/baseItems. amount={}, baseItems={}",
                    item.getAmount(),
                    item.getBaseItems().size());
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return true;
        }

        // Reject unsupported shapes before creating recipient-owned rows.
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem == null
                    || item.getItemAmount(baseItem.getId()) > 1
                    || baseItem.getType() == FurnitureType.BADGE
                    || Item.isBot(baseItem)
                    || Item.isPet(baseItem)
                    || baseItem.getName().contains("avatar_effect")
                    || baseItem.getInteractionType().getType() == InteractionGuildFurni.class
                    || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
                this.client.sendResponse(
                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return true;
            }
        }

        return false;
    }

    /** Creates what the gift holds for the recipient and adds it to {@code itemsList}; false once the sender has been answered. */
    private boolean createGiftContents(
            CatalogItem item,
            User user,
            int userId,
            Connection connection,
            String extraData,
            boolean badgeFound,
            int limitedStack,
            int limitedNumber,
            Set<UserItem> itemsList)
            throws SQLException {
        for (Item baseItem : item.getBaseItems()) {
            if (item.getItemAmount(baseItem.getId()) > 1) {
                LOGGER.debug("unsupported item amount > 1 for baseItemId={}", baseItem.getId());
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return false;
            }

            for (int k = 0; k < item.getItemAmount(baseItem.getId()); k++) {
                if (!baseItem.getName().contains("avatar_effect")) {
                    if (baseItem.getType() == FurnitureType.BADGE) {
                        if (!badgeFound) {
                            if (user != null) {
                                // Written on this connection first, so the badge the recipient holds has its id.
                                UserBadge badge = new UserBadge(0, baseItem.getName(), 0, user);
                                badge.insert(connection);
                                user.getInventory().getBadgesComponent().addBadge(badge);
                            } else {
                                try (PreparedStatement statement = connection.prepareStatement(
                                        "INSERT INTO users_badges (user_id, badge_code) VALUES (?, ?)")) {
                                    statement.setInt(1, userId);
                                    statement.setString(2, baseItem.getName());
                                    statement.execute();
                                }
                            }

                            badgeFound = true;
                        }
                    } else if (item.getName().startsWith("rentable_bot_")) {
                        LOGGER.debug("rentable bot gifts not supported");
                        this.client.sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return false;
                    } else if (Item.isPet(baseItem)) {
                        LOGGER.debug("pet gifts not supported");
                        this.client.sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return false;
                    } else {
                        extraData = this.engraveExtraData(baseItem, user, extraData);

                        if (!this.createGiftedFurniture(
                                connection, baseItem, userId, limitedStack, limitedNumber, extraData, itemsList)) {
                            return false;
                        }
                    }
                } else {
                    LOGGER.debug("avatar_effect not supported");
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    this.client.sendResponse(
                            new GenericAlertComposer(Emulator.getTexts().getValue("error.catalog.buy.not_yet")));
                    return false;
                }
            }
        }

        return true;
    }

    /** A trophy or a badge display carries the sender and the date; a display with a badge the recipient lacks is reported. */
    private String engraveExtraData(Item baseItem, User user, String extraData) {
        if (baseItem.getInteractionType().getType() == InteractionTrophy.class
                || baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class) {
            if (baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class
                    && user != null
                    && !user.getClient()
                            .getUser()
                            .getInventory()
                            .getBadgesComponent()
                            .hasBadge(extraData)) {
                ScripterManager.scripterDetected(
                        user.getClient(),
                        Emulator.getTexts()
                                .getValue("scripter.warning.catalog.badge_display")
                                .replace(
                                        "%username%",
                                        user.getClient().getUser().getUserInfo().getUsername())
                                .replace("%badge%", extraData));
                extraData = "UMAD";
            }

            extraData = this.client.getUser().getUserInfo().getUsername()
                    + (char) 9
                    + Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                    + "-"
                    + (Calendar.getInstance().get(Calendar.MONTH) + 1)
                    + "-"
                    + Calendar.getInstance().get(Calendar.YEAR)
                    + (char) 9
                    + extraData;
        }

        return extraData;
    }

    /**
     * Creates one furni of the gift, both halves for a teleporter, with no owner until the gift is opened;
     * false once the sender has been answered.
     */
    private boolean createGiftedFurniture(
            Connection connection,
            Item baseItem,
            int userId,
            int limitedStack,
            int limitedNumber,
            String extraData,
            Set<UserItem> itemsList)
            throws SQLException {
        // Every teleporter kind (tile and room linker included) comes as a pair,
        // exactly as a purchase for oneself does.
        if (InteractionTeleport.class.isAssignableFrom(
                baseItem.getInteractionType().getType())) {

            UserItem teleportOne = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createGiftContent(connection, userId, baseItem, limitedStack, limitedNumber, extraData);
            UserItem teleportTwo = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createGiftContent(connection, userId, baseItem, limitedStack, limitedNumber, extraData);

            if (teleportOne == null || teleportTwo == null) {
                // The half that was created is rolled back with the rest of the gift.
                if (teleportOne != null) itemsList.add(teleportOne);
                if (teleportTwo != null) itemsList.add(teleportTwo);
                LOGGER.debug(
                        "teleport creation failed. baseItemId={}, teleportOneNull={}, teleportTwoNull={}",
                        baseItem.getId(),
                        teleportOne == null,
                        teleportTwo == null);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            Emulator.getGameEnvironment()
                    .getItemManager()
                    .insertTeleportPair(connection, teleportOne.getId(), teleportTwo.getId());
            itemsList.add(teleportOne);
            itemsList.add(teleportTwo);

        } else if (baseItem.getInteractionType().getType() == InteractionHopper.class) {
            UserItem userItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createGiftContent(connection, userId, baseItem, limitedStack, limitedNumber, extraData);

            if (userItem == null) {
                LOGGER.debug("hopper creation failed. baseItemId={}", baseItem.getId());
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            Emulator.getGameEnvironment().getItemManager().insertHopper(connection, userItem);
            itemsList.add(userItem);

        } else if (baseItem.getInteractionType().getType() == InteractionGuildFurni.class
                || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
            UserItem createdItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createGiftContent(connection, userId, baseItem, limitedStack, limitedNumber, extraData);

            if (createdItem == null) {
                LOGGER.debug("guild item creation failed. baseItemId={}", baseItem.getId());
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            if (!(createdItem instanceof InteractionGuildFurni)) {
                LOGGER.debug(
                        "created guild item has wrong class -> {}",
                        createdItem.getClass().getName());
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            InteractionGuildFurni userItem = (InteractionGuildFurni) createdItem;
            userItem.setExtradata("");
            userItem.needsUpdate(true);

            int guildId;
            try {
                guildId = Integer.parseInt(extraData);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            Emulator.getThreading().run(userItem);
            Emulator.getGameEnvironment().getGuildManager().setGuild(userItem, guildId);
            itemsList.add(userItem);
        } else {
            UserItem userItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createGiftContent(connection, userId, baseItem, limitedStack, limitedNumber, extraData);

            if (userItem == null) {
                LOGGER.debug(
                        "normal item creation failed. baseItemId={}, baseItemName={}",
                        baseItem.getId(),
                        baseItem.getName());
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            itemsList.add(userItem);
        }

        return true;
    }

    /** The extra data of the gift box: what it holds, its look and its card; null once the sender has been answered. */
    private String buildGiftData(Set<UserItem> itemsList, int color, int ribbonId, boolean showName, String message) {
        if (itemsList.isEmpty()) {
            LOGGER.debug("itemsList empty before giftData");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return null;
        }

        StringBuilder giftData = new StringBuilder(itemsList.size() + "\t");

        for (UserItem i : itemsList) {
            if (i == null) {
                LOGGER.debug("null item detected inside itemsList");
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return null;
            }

            giftData.append(i.getId()).append("\t");
        }

        giftData.append(color)
                .append("\t")
                .append(ribbonId)
                .append("\t")
                .append(showName ? "1" : "0")
                .append("\t")
                .append(message.replace("\t", ""))
                .append("\t")
                .append(this.client.getUser().getUserInfo().getUsername())
                .append("\t")
                .append(this.client.getUser().getUserInfo().getLook())
                // Staff gifts are trusted: the card then shows the staff banner instead of the scam warning.
                .append(InteractionGift.trustedSenderField(
                        this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)));

        return giftData.toString();
    }

    /** Puts the gift in the inventory of a recipient who is online and tells them who sent it. */
    private void deliverToRecipient(User user, UserItem gift, boolean showName) {
        if (user == null) return;

        user.getClient().sendResponse(new AddUserItemComposer(gift));
        user.getClient().getUser().getInventory().getItemsComponent().addItem(gift);
        user.getClient().sendResponse(new InventoryRefreshComposer());

        Map<String, String> keys = new HashMap<>();
        keys.put("display", "BUBBLE");
        keys.put("image", "${image.library.url}notifications/gift.gif");
        keys.put("message", Emulator.getTexts().getValue("generic.gift.received.anonymous"));

        if (showName) {
            keys.put(
                    "message",
                    Emulator.getTexts()
                            .getValue("generic.gift.received")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }

        user.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
    }

    private boolean isClubOfferPage(CatalogPage page) {
        return page instanceof ClubBuyLayout
                || page instanceof VipBuyLayout
                || page instanceof BuildersClubFrontPageLayout
                || page instanceof BuildersClubAddonsLayout
                || page instanceof BuildersClubLoyaltyLayout;
    }

    private boolean canAccessPage(CatalogPage page) {
        return CatalogPageAccessPolicy.canAccess(
                page,
                this.client.getUser().getUserInfo().getRank().getId(),
                this.client.getUser().getUserStats().hasActiveClub());
    }

    private int getClubOfferWindowId(CatalogPage page) {
        if (page instanceof BuildersClubAddonsLayout) {
            return ClubOffer.WINDOW_BUILDERS_CLUB_ADDONS;
        }

        if (page instanceof BuildersClubFrontPageLayout || page instanceof BuildersClubLoyaltyLayout) {
            return ClubOffer.WINDOW_BUILDERS_CLUB;
        }

        return ClubOffer.WINDOW_CLUB;
    }

    private void handleClubOfferGift(CatalogPage page, int offerId, String username) {
        ClubOffer offer =
                Emulator.getGameEnvironment().getCatalogManager().clubOffers.get(offerId);

        if (offer == null || !offer.belongsToWindow(this.getClubOfferWindowId(page))) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (!offer.isGiftable()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (offer.isBuildersClubAddon()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        int totalCredits;
        int totalPoints;
        int duration;
        try {
            totalCredits = CatalogPurchaseMath.requireNonNegative(offer.getCredits(), "club gift credit price");
            totalPoints = CatalogPurchaseMath.requireNonNegative(offer.getPoints(), "club gift points price");
            duration = CatalogPurchaseMath.checkedSubscriptionSeconds(offer.getDays());
        } catch (IllegalArgumentException e) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (totalCredits > this.client.getUser().getUserInfo().getCredits()
                || totalPoints > this.client.getUser().getUserInfo().getCurrencyAmount(offer.getPointsType())) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        User recipient = Emulator.getGameEnvironment().getUserManager().getUser(username);
        int recipientId = this.resolveClubGiftRecipientId(recipient, username);

        if (recipientId == 0) {
            this.client.sendResponse(new GiftReceiverNotFoundComposer());
            return;
        }

        if (recipientId == this.client.getUser().getUserInfo().getId()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        String subscriptionType = offer.isBuildersClubSubscription() ? Subscription.BUILDERS_CLUB : Subscription.CLUB;

        int paidCredits = this.client.getUser().hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : totalCredits;
        boolean hasInfinitePoints = offer.getPointsType() == 0
                ? this.client.getUser().hasPermission(Permission.ACC_INFINITE_PIXELS)
                : this.client.getUser().hasPermission(Permission.ACC_INFINITE_POINTS);
        int paidPoints = hasInfinitePoints ? 0 : totalPoints;
        if (!CatalogPaymentService.tryTake(this.client.getUser(), paidCredits, offer.getPointsType(), paidPoints)) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        boolean extended;
        try {
            extended = this.extendClub(recipient, recipientId, subscriptionType, duration);
        } catch (RuntimeException e) {
            CatalogPaymentService.refund(this.client.getUser(), paidCredits, offer.getPointsType(), paidPoints);
            throw e;
        }

        if (!extended) {
            CatalogPaymentService.refund(this.client.getUser(), paidCredits, offer.getPointsType(), paidPoints);
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        // A club gift starts the gift cooldown, as an item gift does; before, club gifts could follow each other
        // without one.
        this.client.getUser().getUserStats().lastGiftTimestamp = Emulator.getIntUnixTimestamp();

        if (recipient != null) this.notifyClubRecipient(recipient, offer, subscriptionType);

        if (this.client.getUser().getUserInfo().getId() != recipientId) {
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GiftGiver"));
        }

        this.client.sendResponse(new PurchaseOKComposer(null));
    }

    /** The id of a club gift's recipient: the online user's, or the one stored for the name; 0 when nobody has it. */
    private int resolveClubGiftRecipientId(User recipient, String username) {
        if (recipient != null) return recipient.getUserInfo().getId();

        int recipientId = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) recipientId = set.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while resolving club gift recipient", e);
        }
        return recipientId;
    }

    /** Adds the days to the recipient's club, in memory when they are online and in the database otherwise. */
    private boolean extendClub(User recipient, int recipientId, String subscriptionType, int duration) {
        if (recipient != null) {
            return recipient.getUserStats().createSubscription(subscriptionType, duration) != null;
        }
        return this.extendOfflineSubscription(recipientId, subscriptionType, duration);
    }

    /** Shows an online recipient their new club and what they received. */
    private void notifyClubRecipient(User recipient, ClubOffer offer, String subscriptionType) {
        recipient
                .getClient()
                .sendResponse(new UserClubComposer(recipient, subscriptionType, UserClubComposer.RESPONSE_TYPE_NORMAL));

        String prefix = Emulator.getTexts().getValue("prereg.reward.you.received", "You have received:");
        String daysWord = Emulator.getTexts().getValue("generic.days", "days");
        String clubLabel = offer.isBuildersClubSubscription() ? "Builders Club" : "HC";
        String giftDescription = clubLabel + " (" + offer.getDays() + " " + daysWord + ")";
        Map<String, String> keys = new HashMap<>();
        keys.put("display", "BUBBLE");
        keys.put("image", "${image.library.url}notifications/gift.gif");
        keys.put("message", prefix + " " + giftDescription);
        recipient.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_GIFT.key, keys));
    }

    private boolean extendOfflineSubscription(int userId, String subscriptionType, int duration) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, duration FROM users_subscriptions WHERE user_id = ? AND subscription_type = ? AND active = 1 ORDER BY id DESC LIMIT 1")) {
                select.setInt(1, userId);
                select.setString(2, subscriptionType);
                try (ResultSet set = select.executeQuery()) {
                    if (set.next()) {
                        int subId = set.getInt("id");
                        int existing = set.getInt("duration");
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE users_subscriptions SET duration = ? WHERE id = ?")) {
                            update.setInt(1, CatalogPurchaseMath.checkedAdd(existing, duration));
                            update.setInt(2, subId);
                            update.executeUpdate();
                            return true;
                        }
                    }
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users_subscriptions (user_id, subscription_type, timestamp_start, duration, active) VALUES (?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, userId);
                insert.setString(2, subscriptionType);
                insert.setInt(3, Emulator.getIntUnixTimestamp());
                insert.setInt(4, duration);
                insert.executeUpdate();
                return true;
            }
        } catch (SQLException | IllegalArgumentException e) {
            LOGGER.error("Caught SQL exception while extending offline subscription", e);
            return false;
        }
    }
}
