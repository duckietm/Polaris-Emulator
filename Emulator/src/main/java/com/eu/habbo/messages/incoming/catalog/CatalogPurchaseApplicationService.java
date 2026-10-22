package com.eu.habbo.messages.incoming.catalog;

import static com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM;
import static com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM;

import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageAccessPolicy;
import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseMath;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.catalog.LtdRaffleManager;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubAddonsLayout;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubFrontPageLayout;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubLoyaltyLayout;
import com.eu.habbo.habbohotel.catalog.layouts.ClubBuyLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RecentPurchasesLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RoomBundleLayout;
import com.eu.habbo.habbohotel.catalog.layouts.VipBuyLayout;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.RankLimits;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.navigator.CanCreateRoomComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CatalogPurchaseApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPurchaseApplicationService.class);

    private final GameClient client;
    private final GameEnvironment environment;
    private final TextsManager texts;
    private final ThreadPooling threading;

    CatalogPurchaseApplicationService(
            GameClient client, GameEnvironment environment, TextsManager texts, ThreadPooling threading) {
        this.client = client;
        this.environment = environment;
        this.texts = texts;
        this.threading = threading;
    }

    void purchase(CatalogPurchaseCommand command) {
        int pageId = command.pageId();
        int itemId = command.itemId();
        String extraData = command.extraData();
        int count = command.count();

        if (this.inventoryFull()) {
            return;
        }

        CatalogManager catalogManager = this.environment.getCatalogManager();
        CatalogPage page = new CatalogPurchasePageResolver(catalogManager)
                .resolve(
                        command,
                        candidate -> CatalogPageAccessPolicy.canAccess(
                                candidate,
                                this.client.getHabbo().getHabboInfo().getRank().getId(),
                                this.client.getHabbo().getHabboStats().hasActiveClub()));

        if (pageId != -12345678 && pageId != -1 && page instanceof RoomBundleLayout roomBundle) {
            this.purchaseRoomBundle(roomBundle);
            return;
        }

        if (page == null) {
            this.refuseMissingPage(catalogManager, pageId, itemId);
            return;
        }

        if (page.getRank() > this.client.getHabbo().getHabboInfo().getRank().getId()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (this.isClubOfferPage(page)) {
            this.purchaseClubOffer(page, itemId, count);
            return;
        }

        CatalogItem item = this.resolveOffer(page, itemId);

        if (this.companionLimitsRefuse(item, extraData)) {
            return;
        }

        // AIR 13 does not sell a limited-edition item to whoever clicked first: the buyers
        // of the same offer are collected for a few seconds and then drawn for the copies
        // that are left. When the raffle takes this purchase it also completes it, so
        // there is nothing more to do here.
        if (LtdRaffleManager.enter(this.environment.getCatalogManager(), page, item, this.client.getHabbo())) {
            return;
        }

        this.environment.getCatalogManager().purchaseItem(page, item, this.client.getHabbo(), count, extraData, false);
    }

    /** Refuses a purchase when the buyer's inventory is over its size, or cannot be read to tell. */
    private boolean inventoryFull() {
        try {
            if (this.client.getHabbo().getInventory().getItemsComponent().itemCount() > HabboInventory.MAXIMUM_ITEMS) {
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                this.client.getHabbo().alert(this.texts.getValue("inventory.full"));
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Purchase refused for user {}: their inventory could not be read", this.buyerId(), e);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return true;
        }

        return false;
    }

    /**
     * Sells the room of a room bundle page: checks the price and the buyer's room limit, takes the price, creates the
     * room (refunding when it cannot be created) and hands over the badges of the bundle.
     */
    private void purchaseRoomBundle(RoomBundleLayout page) {
        CatalogItem roomBundleItem = null;
        for (CatalogItem object : page.getCatalogItems().values()) {
            roomBundleItem = object;
            break;
        }

        if (roomBundleItem == null) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        int roomCredits;
        int roomPoints;
        try {
            roomCredits =
                    CatalogPurchaseMath.requireNonNegative(roomBundleItem.getCredits(), "room bundle credit price");
            roomPoints = CatalogPurchaseMath.requireNonNegative(roomBundleItem.getPoints(), "room bundle points price");
        } catch (IllegalArgumentException e) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (this.refusedForBalance(roomCredits, roomPoints, roomBundleItem.getPointsType())) {
            return;
        }
        int roomCount = this.environment
                .getRoomManager()
                .getRoomsForHabbo(this.client.getHabbo())
                .size();
        int maxRooms = RankLimits.rooms(this.client.getHabbo());

        if (roomCount >= maxRooms) { // checks if a user has the maximum rooms
            this.client.sendResponse(
                    new CanCreateRoomComposer(roomCount, maxRooms)); // if so throws the max room error.
            this.client.sendResponse(new PurchaseOKComposer(
                    null)); // Send this so the alert disappears, not sure if this is how it should be
            // handled :S
            return;
        }
        // One purchase at a time per buyer, paid before the room exists: the payment service takes the price
        // against the ledger at once, so a balance spent meanwhile refuses the purchase instead of leaving a free room.
        synchronized (this.client.getHabbo().getHabboStats()) {
            if (this.client.getHabbo().getHabboStats().isPurchasingFurniture) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }
            this.client.getHabbo().getHabboStats().isPurchasingFurniture = true;
        }

        try {
            int paidCredits = this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : roomCredits;
            int paidPoints = this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : roomPoints;
            int pointsType = roomBundleItem.getPointsType();
            if (!CatalogPaymentService.tryTake(this.client.getHabbo(), paidCredits, pointsType, paidPoints)) {
                this.client.sendResponse(
                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return;
            }

            try {
                page.buyRoom(this.client.getHabbo());
            } catch (RuntimeException e) {
                LOGGER.error("Room bundle purchase failed for user {}", this.buyerId(), e);
                CatalogPaymentService.refund(this.client.getHabbo(), paidCredits, pointsType, paidPoints);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            this.client.sendResponse(new PurchaseOKComposer()); // Sends the composer to close the window.

            this.giveBundleBadges(roomBundleItem);
        } finally {
            synchronized (this.client.getHabbo().getHabboStats()) {
                this.client.getHabbo().getHabboStats().isPurchasingFurniture = false;
            }
        }
    }

    /**
     * Refuses a price the buyer's balance does not cover with NOT_ENOUGH_BALANCE, the reply Habbo gives and the
     * client turns into its "not enough credits" or "not enough points" dialog.
     */
    private boolean refusedForBalance(int credits, int points, int pointsType) {
        boolean notEnoughCredits =
                credits > this.client.getHabbo().getHabboInfo().getCredits();
        boolean notEnoughPoints = points > this.client.getHabbo().getHabboInfo().getCurrencyAmount(pointsType);
        if (!notEnoughCredits && !notEnoughPoints) {
            return false;
        }

        this.client.sendResponse(new NotEnoughPointsTypeComposer(notEnoughCredits, notEnoughPoints, pointsType));
        return true;
    }

    /** Gives the buyer each badge of a bought room bundle they do not have yet. */
    private void giveBundleBadges(CatalogItem roomBundleItem) {
        roomBundleItem.getBaseItems().stream()
                .filter(i -> i.getType() == FurnitureType.BADGE)
                .forEach(i -> {
                    if (!this.client
                            .getHabbo()
                            .getInventory()
                            .getBadgesComponent()
                            .hasBadge(i.getName())) {
                        HabboBadge badge = new HabboBadge(0, i.getName(), 0, this.client.getHabbo());
                        this.threading.run(badge);
                        this.client
                                .getHabbo()
                                .getInventory()
                                .getBadgesComponent()
                                .addBadge(badge);
                        this.client.sendResponse(new AddUserBadgeComposer(badge));
                        Map<String, String> keys = new HashMap<>();
                        keys.put("display", "BUBBLE");
                        keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
                        keys.put("message", this.texts.getValue("commands.generic.cmd_badge.received"));
                        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
                    }
                });
    }

    /** Logs why a purchase has no page the buyer may use and tells them it failed. */
    private void refuseMissingPage(CatalogManager catalogManager, int pageId, int itemId) {
        CatalogPage requested = catalogManager.getCatalogPage(pageId);
        LOGGER.warn(
                "Purchase refused for user {}: catalog page {} (offer {}) {}",
                this.buyerId(),
                pageId,
                itemId,
                requested == null
                        ? "does not exist"
                        : "is not open to them (enabled=" + requested.isEnabled() + ", min_rank=" + requested.getRank()
                                + ", club_only=" + requested.isClubOnly() + ")");
        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
    }

    /**
     * Sells a Habbo Club, Builders Club or Builders Club addon offer of the page's window, count times, one purchase
     * at a time per buyer: takes the price, then grants the subscription or the addon furni, refunding when the
     * subscription cannot be created.
     */
    private void purchaseClubOffer(CatalogPage page, int itemId, int count) {
        synchronized (this.client.getHabbo().getHabboStats()) {
            if (this.client.getHabbo().getHabboStats().isPurchasingFurniture) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }
            this.client.getHabbo().getHabboStats().isPurchasingFurniture = true;
        }

        try {
            ClubOffer item = this.environment.getCatalogManager().clubOffers.get(itemId);

            if (item == null || !item.belongsToWindow(this.getClubOfferWindowId(page))) {
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return;
            }

            int totalDays;
            int totalCredits;
            int totalDuckets;
            int subscriptionSeconds;
            try {
                totalDays = CatalogPurchaseMath.checkedPrice(item.getDays(), count);
                totalCredits = CatalogPurchaseMath.checkedPrice(item.getCredits(), count);
                totalDuckets = CatalogPurchaseMath.checkedPrice(item.getPoints(), count);
                subscriptionSeconds =
                        item.isBuildersClubAddon() ? 0 : CatalogPurchaseMath.checkedSubscriptionSeconds(totalDays);
            } catch (IllegalArgumentException e) {
                this.client.sendResponse(
                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return;
            }

            if (totalDays > 0) {
                if (this.refusedForBalance(totalCredits, totalDuckets, item.getPointsType())) {
                    return;
                }

                int paidCredits =
                        this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : totalCredits;
                int paidPoints =
                        this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : totalDuckets;
                if (!CatalogPaymentService.tryTake(
                        this.client.getHabbo(), paidCredits, item.getPointsType(), paidPoints)) {
                    this.client.sendResponse(
                            new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                    return;
                }

                if (item.isBuildersClubAddon()) {
                    this.client.getHabbo().getHabboStats().addBuildersClubBonusFurni(totalDays);
                    this.client.sendResponse(
                            new BuildersClubFurniCountComposer(BuildersClubRoomSupport.getTrackedFurniCount(
                                    this.client.getHabbo().getHabboInfo().getId())));
                    this.client.sendResponse(new BuildersClubSubscriptionStatusComposer(this.client.getHabbo()));
                } else {
                    String subscriptionType =
                            item.isBuildersClubSubscription() ? Subscription.BUILDERS_CLUB : Subscription.HABBO_CLUB;

                    if (item.isBuildersClubSubscription()) {
                        // Raised before the subscription so its made/extended notice shows the new limit.
                        BuildersClubRoomSupport.raisePackageFurniLimit(this.client.getHabbo(), item.getDays());
                    }

                    if (this.client.getHabbo().getHabboStats().createSubscription(subscriptionType, subscriptionSeconds)
                            == null) {
                        CatalogPaymentService.refund(
                                this.client.getHabbo(), paidCredits, item.getPointsType(), paidPoints);
                        this.client.sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return;
                    }
                }

                this.client.sendResponse(new PurchaseOKComposer(null));
                this.client.sendResponse(new InventoryRefreshComposer());

                this.client.getHabbo().getHabboStats().run();
            }
        } finally {
            synchronized (this.client.getHabbo().getHabboStats()) {
                this.client.getHabbo().getHabboStats().isPurchasingFurniture = false;
            }
        }
    }

    /**
     * The offer the buyer asked for: from their recent purchases on that page, else by catalog item id, else by the
     * offer id the search results send. Null, logged, when the page does not hold it.
     */
    private CatalogItem resolveOffer(CatalogPage page, int itemId) {
        CatalogItem item;

        if (page instanceof RecentPurchasesLayout)
            item = this.client.getHabbo().getHabboStats().getRecentPurchase(itemId);
        else item = page.getCatalogItem(itemId);

        if (item == null && !(page instanceof RecentPurchasesLayout)) {
            for (CatalogItem candidate : page.getCatalogItems().values()) {
                if (candidate != null && candidate.getOfferId() == itemId) {
                    item = candidate;
                    break;
                }
            }
        }

        if (item == null) {
            LOGGER.warn(
                    "Purchase refused for user {}: offer {} is not on catalog page {} ({})",
                    this.buyerId(),
                    itemId,
                    page.getId(),
                    page.getCaption());
        }

        return item;
    }

    /**
     * Refuses a bot when the buyer's bot inventory is full, and a pet when their pet inventory is full or the name,
     * race and colour they sent are not a valid pet.
     */
    private boolean companionLimitsRefuse(CatalogItem item, String extraData) {
        boolean itemHasBot = false;
        boolean itemHasPet = false;

        if (item != null) {
            for (Item baseItem : item.getBaseItems()) {
                if (baseItem == null) continue;
                if (Item.isBot(baseItem)) itemHasBot = true;
                if (Item.isPet(baseItem)) itemHasPet = true;
            }
        }

        if (itemHasBot
                && !this.client.getHabbo().hasPermission(Permission.ACC_UNLIMITED_BOTS)
                && this.client
                                .getHabbo()
                                .getInventory()
                                .getBotsComponent()
                                .getBots()
                                .size()
                        >= BotManager.MAXIMUM_BOT_INVENTORY_SIZE) {
            this.client
                    .getHabbo()
                    .alert(this.texts
                            .getValue("error.bots.max.inventory")
                            .replace("%amount%", BotManager.MAXIMUM_BOT_INVENTORY_SIZE + ""));
            return true;
        }

        if (itemHasPet) {
            if (!this.client.getHabbo().hasPermission(Permission.ACC_UNLIMITED_PETS)
                    && this.client
                                    .getHabbo()
                                    .getInventory()
                                    .getPetsComponent()
                                    .getPets()
                                    .size()
                            >= PetManager.MAXIMUM_PET_INVENTORY_SIZE) {
                this.client
                        .getHabbo()
                        .alert(this.texts
                                .getValue("error.pets.max.inventory")
                                .replace("%amount%", PetManager.MAXIMUM_PET_INVENTORY_SIZE + ""));
                return true;
            }
            String[] check = extraData.split("\n");
            if ((check.length != 3)
                    || (check[0].length() < PET_NAME_LENGTH_MINIMUM)
                    || (check[0].length() > PET_NAME_LENGTH_MAXIMUM)
                    || (!StringUtils.isAlphanumeric(check[0]))) {
                return true;
            }
        }

        return false;
    }

    private boolean isClubOfferPage(CatalogPage page) {
        return page instanceof ClubBuyLayout
                || page instanceof VipBuyLayout
                || page instanceof BuildersClubFrontPageLayout
                || page instanceof BuildersClubAddonsLayout
                || page instanceof BuildersClubLoyaltyLayout;
    }

    private int getClubOfferWindowId(CatalogPage page) {
        if (page instanceof BuildersClubAddonsLayout) {
            return ClubOffer.WINDOW_BUILDERS_CLUB_ADDONS;
        }

        if (page instanceof BuildersClubFrontPageLayout || page instanceof BuildersClubLoyaltyLayout) {
            return ClubOffer.WINDOW_BUILDERS_CLUB;
        }

        return ClubOffer.WINDOW_HABBO_CLUB;
    }

    /** For refusal logs only; never throws on a half-initialised client. */
    private int buyerId() {
        return this.client.getHabbo() != null && this.client.getHabbo().getHabboInfo() != null
                ? this.client.getHabbo().getHabboInfo().getId()
                : 0;
    }
}
