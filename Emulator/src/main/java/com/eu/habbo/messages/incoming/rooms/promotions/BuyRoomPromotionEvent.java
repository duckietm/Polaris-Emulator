package com.eu.habbo.messages.incoming.rooms.promotions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageAccessPolicy;
import com.eu.habbo.habbohotel.messenger.FriendActivityNotifier;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import com.eu.habbo.messages.outgoing.rooms.promotions.RoomPromotionMessageComposer;

public class BuyRoomPromotionEvent extends MessageHandler {
    public static volatile String ROOM_PROMOTION_BADGE = "RADZZ";

    @Override
    public void handle() throws Exception {
        int pageId = this.packet.readInt();
        int itemId = this.packet.readInt();
        int roomId = this.packet.readInt();
        String title =
                RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_PROMOTION_TITLE_LENGTH);
        this.packet.readBoolean(); // extendedPromotion - not used
        String description = RoomItemInputGuard.trimToMax(
                this.packet.readString(), RoomItemInputGuard.MAX_PROMOTION_DESCRIPTION_LENGTH);
        int categoryId = this.packet.readInt();

        if (NewNavigatorEventCategoriesComposer.CATEGORIES.stream().noneMatch(c -> c.getId() == categoryId)) return;

        Habbo habbo = this.client.getHabbo();
        CatalogPage page = environment().getCatalogManager().getCatalogPage(pageId);

        // The layout alone let anyone buy from a hidden, disabled, staff-only or club-only "roomads" page.
        if (page == null || !page.getLayout().equals("roomads") || !mayUse(page, habbo)) return;

        CatalogItem item = page.getCatalogItem(itemId);
        if (item != null) {
            if (habbo.getHabboInfo().canBuy(item)) {
                Room room = environment().getRoomManager().loadRoom(roomId);

                if (room == null) return;

                if (!(room.isOwner(habbo)
                        || room.hasRights(habbo)
                        || room.getGuildRightLevel(habbo).equals(RoomRightLevels.GUILD_ADMIN))) {
                    return;
                }

                // The event shows in the navigator, in the room and in friends' notifications, hotel-wide.
                title = environment().getWordFilter().filter(title, habbo);
                description = environment().getWordFilter().filter(description, habbo);

                if (!charge(habbo, item)) {
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    return;
                }

                boolean newEvent = !room.isPromoted();
                if (newEvent) {
                    room.createPromotion(title, description, categoryId);
                } else {
                    room.getPromotion().addEndTimestamp(120 * 60);
                }

                if (room.isPromoted()) {
                    this.client.sendResponse(new PurchaseOKComposer());
                    room.sendComposer(new RoomPromotionMessageComposer(room, room.getPromotion()).compose());
                    if (newEvent) {
                        FriendActivityNotifier.roomEventStarted(
                                habbo, room.getPromotion().getTitle());
                    }

                    if (!habbo.getInventory()
                            .getBadgesComponent()
                            .hasBadge(BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE)) {
                        habbo.addBadge(BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE);
                    }
                } else {
                    refund(habbo, item);
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                }
            }
        }
    }

    static boolean mayUse(CatalogPage page, Habbo habbo) {
        return CatalogPageAccessPolicy.canAccess(
                page,
                habbo.getHabboInfo().getRank().getId(),
                habbo.getHabboStats().hasActiveClub());
    }

    /**
     * Takes the price before the event exists. It used to be taken afterwards with giveCredits(-price), which never
     * refuses: a balance spent meanwhile went negative, or the event came for free when the ledger said no.
     */
    static boolean charge(Habbo habbo, CatalogItem item) {
        boolean credits = item.getCredits() > 0 && !habbo.hasPermission(Permission.ACC_INFINITE_CREDITS);
        boolean points = item.getPoints() > 0 && !habbo.hasPermission(Permission.ACC_INFINITE_POINTS);

        if (credits && !habbo.tryTakeCredits(item.getCredits())) return false;

        if (points && !habbo.tryTakePoints(item.getPointsType(), item.getPoints())) {
            if (credits) habbo.giveCredits(item.getCredits());
            return false;
        }

        return true;
    }

    static void refund(Habbo habbo, CatalogItem item) {
        if (item.getCredits() > 0 && !habbo.hasPermission(Permission.ACC_INFINITE_CREDITS)) {
            habbo.giveCredits(item.getCredits());
        }
        if (item.getPoints() > 0 && !habbo.hasPermission(Permission.ACC_INFINITE_POINTS)) {
            habbo.givePoints(item.getPointsType(), item.getPoints());
        }
    }

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }
}
