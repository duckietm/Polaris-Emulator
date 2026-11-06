package polaris.messages.incoming.rooms.promotions;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageAccessPolicy;
import polaris.hotel.messenger.FriendActivityNotifier;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomRightLevels;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import polaris.messages.outgoing.rooms.promotions.RoomPromotionMessageComposer;

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

        User user = this.client.getUser();
        CatalogPage page = environment().getCatalogManager().getCatalogPage(pageId);

        // The layout alone let anyone buy from a hidden, disabled, staff-only or club-only "roomads" page.
        if (page == null || !page.getLayout().equals("roomads") || !mayUse(page, user)) return;

        CatalogItem item = page.getCatalogItem(itemId);
        if (item != null) {
            if (user.getUserInfo().canBuy(item)) {
                Room room = environment().getRoomManager().loadRoom(roomId);

                if (room == null) return;

                if (!(room.isOwner(user)
                        || room.hasRights(user)
                        || room.getGuildRightLevel(user).equals(RoomRightLevels.GUILD_ADMIN))) {
                    return;
                }

                // The event shows in the navigator, in the room and in friends' notifications, hotel-wide.
                title = environment().getWordFilter().filter(title, user);
                description = environment().getWordFilter().filter(description, user);

                if (!charge(user, item)) {
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
                                user, room.getPromotion().getTitle());
                    }

                    if (!user.getInventory()
                            .getBadgesComponent()
                            .hasBadge(BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE)) {
                        user.addBadge(BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE);
                    }
                } else {
                    refund(user, item);
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                }
            }
        }
    }

    static boolean mayUse(CatalogPage page, User user) {
        return CatalogPageAccessPolicy.canAccess(
                page, user.getUserInfo().getRank().getId(), user.getUserStats().hasActiveClub());
    }

    /**
     * Takes the price before the event exists. It used to be taken afterwards with giveCredits(-price), which never
     * refuses: a balance spent meanwhile went negative, or the event came for free when the ledger said no.
     */
    static boolean charge(User user, CatalogItem item) {
        boolean credits = item.getCredits() > 0 && !user.hasPermission(Permission.ACC_INFINITE_CREDITS);
        boolean points = item.getPoints() > 0 && !user.hasPermission(Permission.ACC_INFINITE_POINTS);

        if (credits && !user.tryTakeCredits(item.getCredits())) return false;

        if (points && !user.tryTakePoints(item.getPointsType(), item.getPoints())) {
            if (credits) user.giveCredits(item.getCredits());
            return false;
        }

        return true;
    }

    static void refund(User user, CatalogItem item) {
        if (item.getCredits() > 0 && !user.hasPermission(Permission.ACC_INFINITE_CREDITS)) {
            user.giveCredits(item.getCredits());
        }
        if (item.getPoints() > 0 && !user.hasPermission(Permission.ACC_INFINITE_POINTS)) {
            user.givePoints(item.getPointsType(), item.getPoints());
        }
    }

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }
}
