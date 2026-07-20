package com.eu.habbo.messages.incoming.catalog;

import static com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM;
import static com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageAccessPolicy;
import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseCommand;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseMath;
import com.eu.habbo.habbohotel.catalog.CatalogPurchasePageService;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubAddonsLayout;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubFrontPageLayout;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubLoyaltyLayout;
import com.eu.habbo.habbohotel.catalog.layouts.ClubBuyLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RecentPurchasesLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RoomBundleLayout;
import com.eu.habbo.habbohotel.catalog.layouts.VipBuyLayout;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.navigator.CanCreateRoomComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class CatalogBuyItemEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public String getRatelimitGroup() {
        return "catalog.purchase";
    }

    @Override
    public void handle() throws Exception {
        if (Emulator.getIntUnixTimestamp() - this.client.getHabbo().getHabboStats().lastPurchaseTimestamp
                >= CatalogManager.PURCHASE_COOLDOWN) {
            this.client.getHabbo().getHabboStats().lastPurchaseTimestamp = Emulator.getIntUnixTimestamp();
            if (ShutdownEmulator.timestamp > 0) {
                this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                        (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
                return;
            }

            CatalogPurchaseCommand command = CatalogPurchaseCommandReader.readFrom(this.packet);
            int pageId = command.pageId();
            int itemId = command.itemId();
            String extraData = command.extraData();
            int count = command.count();

            try {
                if (this.client.getHabbo().getInventory().getItemsComponent().itemCount()
                        > HabboInventory.MAXIMUM_ITEMS) {
                    this.client.sendResponse(
                            new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                    this.client.getHabbo().alert(Emulator.getTexts().getValue("inventory.full"));
                    return;
                }
            } catch (Exception e) {
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            }

            CatalogManager catalogManager = Emulator.getGameEnvironment().getCatalogManager();
            CatalogPage page = new CatalogPurchasePageService(catalogManager)
                    .resolve(
                            command,
                            candidate -> CatalogPageAccessPolicy.canAccess(
                                    candidate,
                                    this.client
                                            .getHabbo()
                                            .getHabboInfo()
                                            .getRank()
                                            .getId(),
                                    this.client.getHabbo().getHabboStats().hasActiveClub()));

            if (pageId != -12345678 && pageId != -1) {
                if (page instanceof RoomBundleLayout) {
                    final CatalogItem[] item = new CatalogItem[1];
                    for (CatalogItem object : page.getCatalogItems().values()) {
                        item[0] = object;
                        break;
                    }

                    CatalogItem roomBundleItem = item[0];
                    if (roomBundleItem == null) {
                        this.client.sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        return;
                    }

                    int roomCredits;
                    int roomPoints;
                    try {
                        roomCredits = CatalogPurchaseMath.requireNonNegative(
                                roomBundleItem.getCredits(), "room bundle credit price");
                        roomPoints = CatalogPurchaseMath.requireNonNegative(
                                roomBundleItem.getPoints(), "room bundle points price");
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(
                                new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                        return;
                    }

                    if (roomCredits > this.client.getHabbo().getHabboInfo().getCredits()
                            || roomPoints
                                    > this.client
                                            .getHabbo()
                                            .getHabboInfo()
                                            .getCurrencyAmount(roomBundleItem.getPointsType())) {
                        this.client.sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        return;
                    }
                    int roomCount = Emulator.getGameEnvironment()
                            .getRoomManager()
                            .getRoomsForHabbo(this.client.getHabbo())
                            .size();
                    int maxRooms = this.client.getHabbo().getHabboStats().hasActiveClub()
                            ? RoomManager.MAXIMUM_ROOMS_HC
                            : RoomManager.MAXIMUM_ROOMS_USER;

                    if (roomCount >= maxRooms) { // checks if a user has the maximum rooms
                        this.client.sendResponse(
                                new CanCreateRoomComposer(roomCount, maxRooms)); // if so throws the max room error.
                        this.client.sendResponse(new PurchaseOKComposer(
                                null)); // Send this so the alert disappears, not sure if this is how it should be
                        // handled :S
                        return;
                    }
                    ((RoomBundleLayout) page).buyRoom(this.client.getHabbo());
                    if (!this.client
                            .getHabbo()
                            .hasPermission(Permission.ACC_INFINITE_CREDITS)) { // if the player has this perm disabled
                        this.client.getHabbo().giveCredits(-roomCredits); // takes their credits away
                    }
                    if (!this.client
                            .getHabbo()
                            .hasPermission(Permission.ACC_INFINITE_POINTS)) { // if the player has this perm disabled
                        this.client
                                .getHabbo()
                                .givePoints(roomBundleItem.getPointsType(), -roomPoints); // takes their points away
                    }
                    this.client.sendResponse(new PurchaseOKComposer()); // Sends the composer to close the window.

                    item[0].getBaseItems().stream()
                            .filter(i -> i.getType() == FurnitureType.BADGE)
                            .forEach(i -> {
                                if (!this.client
                                        .getHabbo()
                                        .getInventory()
                                        .getBadgesComponent()
                                        .hasBadge(i.getName())) {
                                    HabboBadge badge = new HabboBadge(0, i.getName(), 0, this.client.getHabbo());
                                    Emulator.getThreading().run(badge);
                                    this.client
                                            .getHabbo()
                                            .getInventory()
                                            .getBadgesComponent()
                                            .addBadge(badge);
                                    this.client.sendResponse(new AddUserBadgeComposer(badge));
                                    Map<String, String> keys = new HashMap<>();
                                    keys.put("display", "BUBBLE");
                                    keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
                                    keys.put(
                                            "message",
                                            Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
                                    this.client.sendResponse(
                                            new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
                                }
                            });

                    return;
                }
            }

            if (page == null) {
                this.client.sendResponse(
                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return;
            }

            if (page.getRank() > this.client.getHabbo().getHabboInfo().getRank().getId()) {
                this.client.sendResponse(
                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return;
            }

            if (this.isClubOfferPage(page)) {
                synchronized (this.client.getHabbo().getHabboStats()) {
                    if (this.client.getHabbo().getHabboStats().isPurchasingFurniture) {
                        this.client.sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        return;
                    }
                    this.client.getHabbo().getHabboStats().isPurchasingFurniture = true;
                }

                try {
                    ClubOffer item = Emulator.getGameEnvironment()
                            .getCatalogManager()
                            .clubOffers
                            .get(itemId);

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
                        subscriptionSeconds = item.isBuildersClubAddon()
                                ? 0
                                : CatalogPurchaseMath.checkedSubscriptionSeconds(totalDays);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(
                                new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                        return;
                    }

                    if (totalDays > 0) {
                        if (this.client.getHabbo().getHabboInfo().getCurrencyAmount(item.getPointsType())
                                < totalDuckets) return;

                        if (this.client.getHabbo().getHabboInfo().getCredits() < totalCredits) return;

                        int paidCredits = this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS)
                                ? 0
                                : totalCredits;
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
                            this.client.sendResponse(new BuildersClubFurniCountComposer(
                                    BuildersClubRoomSupport.getTrackedFurniCount(this.client
                                            .getHabbo()
                                            .getHabboInfo()
                                            .getId())));
                            this.client.sendResponse(
                                    new BuildersClubSubscriptionStatusComposer(this.client.getHabbo()));
                        } else {
                            String subscriptionType = item.isBuildersClubSubscription()
                                    ? Subscription.BUILDERS_CLUB
                                    : Subscription.HABBO_CLUB;

                            if (this.client
                                            .getHabbo()
                                            .getHabboStats()
                                            .createSubscription(subscriptionType, subscriptionSeconds)
                                    == null) {
                                CatalogPaymentService.refund(
                                        this.client.getHabbo(), paidCredits, item.getPointsType(), paidPoints);
                                this.client.sendResponse(
                                        new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR)
                                                .compose());
                                return;
                            }
                        }

                        this.client.sendResponse(new PurchaseOKComposer(null));
                        this.client.sendResponse(new InventoryRefreshComposer());

                        this.client.getHabbo().getHabboStats().run();
                    }
                    return;
                } finally {
                    synchronized (this.client.getHabbo().getHabboStats()) {
                        this.client.getHabbo().getHabboStats().isPurchasingFurniture = false;
                    }
                }
            }

            CatalogItem item;

            if (page instanceof RecentPurchasesLayout)
                item = this.client
                        .getHabbo()
                        .getHabboStats()
                        .getRecentPurchases()
                        .get(itemId);
            else item = page.getCatalogItem(itemId);

            if (item == null && !(page instanceof RecentPurchasesLayout)) {
                for (CatalogItem candidate : page.getCatalogItems().values()) {
                    if (candidate != null && candidate.getOfferId() == itemId) {
                        item = candidate;
                        break;
                    }
                }
            }

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
                        .alert(Emulator.getTexts()
                                .getValue("error.bots.max.inventory")
                                .replace("%amount%", BotManager.MAXIMUM_BOT_INVENTORY_SIZE + ""));
                return;
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
                            .alert(Emulator.getTexts()
                                    .getValue("error.pets.max.inventory")
                                    .replace("%amount%", PetManager.MAXIMUM_PET_INVENTORY_SIZE + ""));
                    return;
                }
                String[] check = extraData.split("\n");
                if ((check.length != 3)
                        || (check[0].length() < PET_NAME_LENGTH_MINIMUM)
                        || (check[0].length() > PET_NAME_LENGTH_MAXIMUM)
                        || (!StringUtils.isAlphanumeric(check[0]))) {
                    return;
                }
            }

            Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .purchaseItem(page, item, this.client.getHabbo(), count, extraData, false);

        } else {
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
        }
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
}
