package polaris.messages.incoming.catalog;

import java.util.Iterator;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionRecolorable;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.catalog.BuildersClubPlacementWarningComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;

public class BuildersClubPlaceWallItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int pageId = this.packet.readInt();
        int offerId = this.packet.readInt();
        String extraData = this.packet.readString();
        String wallPosition = this.packet.readString();
        boolean confirmed = this.packet.readBoolean();

        // The same check as for a wall furni placed from the inventory: the position is stored and sent to the room.
        if (!RoomItemInputGuard.isValidWallPosition(wallPosition)) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        int placementUserId = BuildersClubRoomSupport.getPlacementPoolUserId(this.client.getUser());

        if (room == null || !this.client.getUser().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        // Playtest (the hotel's own rule beyond AIR): no Builders Club furni is placed either.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        if (!BuildersClubRoomSupport.canPlaceInCurrentRoom(this.client.getUser())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "builder.placement_widget.error.not_group_admin"));
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getUser());
            return;
        }

        if (placementUserId <= 0) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        // Members build up to their package limit, trial users up to the trial limit.
        if (BuildersClubRoomSupport.getTrackedFurniCount(placementUserId)
                >= BuildersClubRoomSupport.getFurniLimit(placementUserId)) {
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "room.error.max_furniture"));
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getUser());
            return;
        }

        if (!BuildersClubRoomSupport.hasActiveMembership(
                        this.client.getUser().getUserInfo().getId())
                && BuildersClubRoomSupport.hasPlacementVisitors(room, this.client.getUser())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "builder.placement_widget.error.visitors"));
            return;
        }

        CatalogItem catalogItem = resolveCatalogItem(pageId, offerId);
        Item baseItem = resolveBaseItem(catalogItem, FurnitureType.WALL);

        if (catalogItem == null || baseItem == null || WiredWebApiOwnership.isWebApiItem(baseItem)) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        if (!confirmed && BuildersClubRoomSupport.placementWouldHideRoom(room)) {
            this.client.sendResponse(
                    BuildersClubPlacementWarningComposer.wall(pageId, offerId, extraData, wallPosition));
            return;
        }

        UserItem item = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(
                        placementUserId,
                        baseItem,
                        0,
                        0,
                        InteractionRecolorable.placementExtradata(baseItem, extraData, catalogItem.getExtradata()));

        if (item == null) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);

        // Tracked before it enters the room, so the room hands it a Builders Club band id before announcing it.
        BuildersClubRoomSupport.trackPlacedItem(item.getId(), placementUserId, room.getId());
        FurnitureMovementError error = room.placeWallFurniAt(item, wallPosition, this.client.getUser());

        if (!error.equals(FurnitureMovementError.NONE)) {
            BuildersClubRoomSupport.deleteTrackedItem(item.getId());
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            return;
        }

        polaris.hotel.quests.QuestProgressEvents.progress(
                this.client.getUser(), polaris.hotel.quests.QuestGoalType.PLACE_BUILDERS_CLUB_FURNI, 1);

        if (BuildersClubRoomSupport.syncRoom(room) == BuildersClubRoomSupport.SyncResult.LOCKED) {
            BuildersClubRoomSupport.sendRoomLockedBubble(room.getOwnerId());
        }

        BuildersClubRoomSupport.sendPlacementStatusForPool(room, placementUserId);
    }

    private CatalogItem resolveCatalogItem(int pageId, int offerId) {
        CatalogItem buildersClubItem =
                Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(offerId, CatalogPageType.BUILDER);

        if (buildersClubItem != null) {
            return buildersClubItem;
        }

        // Only Builders Club offers: the normal catalog's offers were placed for free, past their page's rank,
        // club, limited and price rules, and could then be redeemed.
        int catalogItemId = Emulator.getGameEnvironment()
                .getCatalogManager()
                .buildersClubOfferDefs
                .get(offerId);

        if (catalogItemId > 0) {
            return Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .getCatalogItem(catalogItemId, CatalogPageType.BUILDER);
        }

        CatalogPage page =
                Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, CatalogPageType.BUILDER);

        if (page == null) {
            return null;
        }

        for (CatalogItem catalogItem : page.getCatalogItems().values()) {
            if (catalogItem.getOfferId() == offerId) {
                return catalogItem;
            }
        }

        return null;
    }

    private Item resolveBaseItem(CatalogItem catalogItem, FurnitureType expectedType) {
        if (catalogItem == null
                || catalogItem.getAmount() != 1
                || catalogItem.getBaseItems().size() != 1) {
            return null;
        }

        Iterator<Item> iterator = catalogItem.getBaseItems().iterator();

        if (!iterator.hasNext()) {
            return null;
        }

        Item baseItem = iterator.next();

        if (baseItem == null || baseItem.getType() != expectedType) {
            return null;
        }

        return baseItem;
    }
}
