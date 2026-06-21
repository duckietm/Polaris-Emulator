package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;

import java.util.Iterator;

public class BuildersClubPlaceWallItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int pageId = this.packet.readInt();
        int offerId = this.packet.readInt();
        String extraData = this.packet.readString();
        String wallPosition = this.packet.readString();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        int placementUserId = BuildersClubRoomSupport.getPlacementPoolUserId(this.client.getHabbo());

        if (room == null || !this.client.getHabbo().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        if (!BuildersClubRoomSupport.canPlaceInCurrentRoom(this.client.getHabbo())) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "builder.placement_widget.error.not_group_admin"));
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getHabbo());
            return;
        }

        if (placementUserId <= 0) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        if (!BuildersClubRoomSupport.hasActiveMembership(this.client.getHabbo().getHabboInfo().getId())) {
            int trackedFurniCount = BuildersClubRoomSupport.getTrackedFurniCount(placementUserId);

            if (trackedFurniCount >= BuildersClubRoomSupport.getFurniLimit(placementUserId)) {
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "room.error.max_furniture"));
                BuildersClubRoomSupport.sendPlacementStatus(this.client.getHabbo());
                return;
            }

            if (BuildersClubRoomSupport.hasPlacementVisitors(room, this.client.getHabbo())) {
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "builder.placement_widget.error.visitors"));
                return;
            }
        }

        CatalogItem catalogItem = resolveCatalogItem(pageId, offerId);
        Item baseItem = resolveBaseItem(catalogItem, FurnitureType.WALL);

        if (catalogItem == null || baseItem == null) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(placementUserId, baseItem, 0, 0, (extraData != null && !extraData.isEmpty()) ? extraData : catalogItem.getExtradata());

        if (item == null) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);

        FurnitureMovementError error = room.placeWallFurniAt(item, wallPosition, this.client.getHabbo());

        if (!error.equals(FurnitureMovementError.NONE)) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            return;
        }

        BuildersClubRoomSupport.trackPlacedItem(item.getId(), placementUserId, room.getId());

        if (BuildersClubRoomSupport.syncRoom(room) == BuildersClubRoomSupport.SyncResult.LOCKED) {
            BuildersClubRoomSupport.sendRoomLockedBubble(room.getOwnerId());
        }

        BuildersClubRoomSupport.sendPlacementStatusForPool(room, placementUserId);
    }

    private CatalogItem resolveCatalogItem(int pageId, int offerId) {
        CatalogItem buildersClubItem = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(offerId, CatalogPageType.BUILDER);

        if (buildersClubItem != null) {
            return buildersClubItem;
        }

        int catalogItemId = Emulator.getGameEnvironment().getCatalogManager().offerDefs.get(offerId);

        if (catalogItemId > 0) {
            return Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(catalogItemId);
        }

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, CatalogPageType.BUILDER);

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
        if (catalogItem == null || catalogItem.getAmount() != 1 || catalogItem.getBaseItems().size() != 1) {
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
