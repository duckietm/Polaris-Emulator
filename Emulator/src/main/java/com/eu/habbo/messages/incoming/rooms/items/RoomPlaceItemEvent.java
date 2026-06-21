package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;

public class RoomPlaceItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String[] values = this.packet.readString().split(" ");

        if (values.length == 0)
            return;

        Integer itemId = RoomItemInputGuard.parseInt(values[0]);
        if (itemId == null || !RoomItemInputGuard.isPositiveId(itemId))
            return;

        if (!this.client.getHabbo().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        HabboItem rentSpace = null;
        if (this.client.getHabbo().getHabboStats().isRentingSpace()) {
            rentSpace = room.getHabboItem(this.client.getHabbo().getHabboStats().rentedItemId);
        }

        HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);

        if (item == null || item.getBaseItem().getInteractionType().getType() == InteractionPostIt.class)
            return;

        if (room.getId() != item.getRoomId() && item.getRoomId() != 0)
            return;

        //TODO move this to canStackAt() though find a way to handle the different bubble alert keys
        if (item instanceof InteractionMoodLight && !room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class).isEmpty()) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_DIMMERS.errorCode));
            return;
        }
        if (item instanceof InteractionJukeBox && !room.getRoomSpecialTypes().getItemsOfType(InteractionJukeBox.class).isEmpty()) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_SOUNDFURNI.errorCode));
            return;
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (values.length < 4)
                return;

            Short x = RoomItemInputGuard.parseShort(values[1]);
            Short y = RoomItemInputGuard.parseShort(values[2]);
            Integer rotation = RoomItemInputGuard.parseInt(values[3]);

            if (x == null || y == null || rotation == null)
                return;

            RoomTile tile = room.getLayout().getTile(x, y);

            if(tile == null)
            {
                String userName  = this.client.getHabbo().getHabboInfo().getUsername();
                int roomId = room.getId();
                ScripterManager.scripterDetected(this.client, "User [" + userName + "] tried to place a furni with itemId [" + itemId + "] at a tile which is not existing in room [" + roomId + "], tile: [" + x + "," + y + "]");
                return;
            }

            HabboItem buildArea = null;
            for (HabboItem area : room.getRoomSpecialTypes().getItemsOfType(InteractionBuildArea.class)) {
                if (((InteractionBuildArea) area).inSquare(tile)) {
                    buildArea = area;
                }
            }

            if ((rentSpace != null || buildArea != null) && !room.hasRights(this.client.getHabbo())) {
                if (item instanceof InteractionRoller ||
                        item instanceof InteractionStackHelper ||
                        item instanceof InteractionStackWalkHelper ||
                        item instanceof InteractionWired ||
                        item instanceof InteractionBackgroundToner ||
                        item instanceof InteractionRoomAds ||
                        item instanceof InteractionCannon ||
                        item instanceof InteractionPuzzleBox ||
                        item.getBaseItem().getType() == FurnitureType.WALL) {
                    this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
                    return;
                }
                if (rentSpace != null && !RoomLayout.squareInSquare(RoomLayout.getRectangle(rentSpace.getX(), rentSpace.getY(), rentSpace.getBaseItem().getWidth(), rentSpace.getBaseItem().getLength(), rentSpace.getRotation()), RoomLayout.getRectangle(x, y, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation))) {
                    this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
                    return;
                }
            }
            FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getHabbo(), tile, rotation);

            if (!error.equals(FurnitureMovementError.NONE)) {
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                return;
            }

            error = room.placeFloorFurniAt(item, tile, rotation, this.client.getHabbo());
            if (!error.equals(FurnitureMovementError.NONE)) {
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                return;
            }
        } else {
            if (values.length < 4)
                return;

            FurnitureMovementError error = room.placeWallFurniAt(item, values[1] + " " + values[2] + " " + values[3], this.client.getHabbo());
            if (!error.equals(FurnitureMovementError.NONE)) {
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                return;
            }
        }

        this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item.getId());
        item.setFromGift(false);

        if (BuildersClubRoomSupport.isTrackedItem(item.getId())) {
            int trackedUserId = BuildersClubRoomSupport.getTrackedUserId(item.getId());

            if (trackedUserId <= 0) {
                trackedUserId = this.client.getHabbo().getHabboInfo().getId();
            }

            item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);
            BuildersClubRoomSupport.trackPlacedItem(item.getId(), trackedUserId, room.getId());

            BuildersClubRoomSupport.SyncResult syncResult = BuildersClubRoomSupport.syncRoom(room);

            if (syncResult == BuildersClubRoomSupport.SyncResult.LOCKED) {
                BuildersClubRoomSupport.sendRoomLockedBubble(room.getOwnerId());
            } else if (syncResult == BuildersClubRoomSupport.SyncResult.UNLOCKED) {
                BuildersClubRoomSupport.sendRoomUnlockedBubble(room.getOwnerId());
            }

            if (trackedUserId == this.client.getHabbo().getHabboInfo().getId()) {
                this.client.sendResponse(new BuildersClubFurniCountComposer(BuildersClubRoomSupport.getTrackedFurniCount(trackedUserId)));
                this.client.sendResponse(new BuildersClubSubscriptionStatusComposer(this.client.getHabbo()));
            }
        }
    }
}
