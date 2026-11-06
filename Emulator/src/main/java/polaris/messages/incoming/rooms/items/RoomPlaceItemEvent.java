package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.FurnitureDirections;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.interactions.InteractionBackgroundToner;
import polaris.hotel.items.interactions.InteractionBuildArea;
import polaris.hotel.items.interactions.InteractionCannon;
import polaris.hotel.items.interactions.InteractionJukeBox;
import polaris.hotel.items.interactions.InteractionMoodLight;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.InteractionPuzzleBox;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.items.interactions.InteractionRoomAds;
import polaris.hotel.items.interactions.InteractionStackHelper;
import polaris.hotel.items.interactions.InteractionStackWalkHelper;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import polaris.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;
import polaris.messages.outgoing.wired.WiredOpenComposer;

public class RoomPlaceItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        String[] values = this.packet.readString().split(" ");

        if (values.length == 0) return;

        Integer itemId = RoomItemInputGuard.parseInt(values[0]);
        if (itemId == null || !RoomItemInputGuard.isPositiveId(itemId)) return;

        if (!this.client.getUser().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        // Playtest (the hotel's own rule beyond AIR): nothing is placed from the inventory, the item stays there.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        UserItem rentSpace = null;
        if (this.client.getUser().getUserStats().isRentingSpace()) {
            rentSpace = room.getUserItem(this.client.getUser().getUserStats().rentedItemId);
        }

        UserItem item = this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);

        if (item == null || item.getBaseItem().getInteractionType().getType() == InteractionPostIt.class) return;

        if (room.getId() != item.getRoomId() && item.getRoomId() != 0) return;

        // TODO move this to canStackAt() though find a way to handle the different bubble alert keys
        if (item instanceof InteractionMoodLight
                && !room.getRoomSpecialTypes()
                        .getItemsOfType(InteractionMoodLight.class)
                        .isEmpty()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_DIMMERS.errorCode));
            return;
        }
        if (item instanceof InteractionJukeBox
                && !room.getRoomSpecialTypes()
                        .getItemsOfType(InteractionJukeBox.class)
                        .isEmpty()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_SOUNDFURNI.errorCode));
            return;
        }

        String webApiRefusal = WiredWebApiOwnership.placementRefusal(room, item);
        if (webApiRefusal != null) {
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "${" + webApiRefusal + "}"));
            return;
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (values.length < 4) return;

            Short x = RoomItemInputGuard.parseShort(values[1]);
            Short y = RoomItemInputGuard.parseShort(values[2]);
            Integer rotation = RoomItemInputGuard.parseInt(values[3]);

            if (x == null || y == null || rotation == null) return;
            // A direction the furni's asset does not list is stored as its default one, as in Habbo.
            rotation = FurnitureDirections.of(item).place(rotation);

            RoomTile tile = room.getLayout().getTile(x, y);

            if (tile == null) {
                String userName = this.client.getUser().getUserInfo().getUsername();
                int roomId = room.getId();
                ScripterManager.scripterDetected(
                        this.client,
                        "User [" + userName + "] tried to place a furni with itemId [" + itemId
                                + "] at a tile which is not existing in room [" + roomId + "], tile: [" + x + "," + y
                                + "]");
                return;
            }

            UserItem buildArea = null;
            for (UserItem area : room.getRoomSpecialTypes().getItemsOfType(InteractionBuildArea.class)) {
                if (((InteractionBuildArea) area).inSquare(tile)) {
                    buildArea = area;
                }
            }

            if ((rentSpace != null || buildArea != null) && !room.hasRights(this.client.getUser())) {
                if (item instanceof InteractionRoller
                        || item instanceof InteractionStackHelper
                        || item instanceof InteractionStackWalkHelper
                        || item instanceof InteractionWired
                        || item instanceof InteractionBackgroundToner
                        || item instanceof InteractionRoomAds
                        || item instanceof InteractionCannon
                        || item instanceof InteractionPuzzleBox
                        || item.getBaseItem().getType() == FurnitureType.WALL) {
                    this.client.sendResponse(new BubbleAlertComposer(
                            BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
                    return;
                }
                if (rentSpace != null
                        && !RoomLayout.squareInSquare(
                                RoomLayout.getRectangle(
                                        rentSpace.getX(),
                                        rentSpace.getY(),
                                        rentSpace.getBaseItem().getWidth(),
                                        rentSpace.getBaseItem().getLength(),
                                        rentSpace.getRotation()),
                                RoomLayout.getRectangle(
                                        x,
                                        y,
                                        item.getBaseItem().getWidth(),
                                        item.getBaseItem().getLength(),
                                        rotation))) {
                    this.client.sendResponse(new BubbleAlertComposer(
                            BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
                    return;
                }
            }
            FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getUser(), tile, rotation);

            if (!error.equals(FurnitureMovementError.NONE)) {
                this.client.sendResponse(
                        new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                return;
            }

            error = room.placeFloorFurniAt(item, tile, rotation, this.client.getUser());
            if (!error.equals(FurnitureMovementError.NONE)) {
                this.client.sendResponse(
                        new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                return;
            }

            // A wired box you just put down is a box you are about to configure: the official client
            // opens its window straight away rather than making you click what you just placed.
            if (item instanceof InteractionWired && room.canInspectWired(this.client.getUser())) {
                this.client.sendResponse(new WiredOpenComposer(item));
            }
        } else {
            if (values.length < 4) return;

            String wallPosition = values[1] + " " + values[2] + " " + values[3];

            if (!RoomItemInputGuard.isValidWallPosition(wallPosition)) return;

            FurnitureMovementError error = room.placeWallFurniAt(item, wallPosition, this.client.getUser());
            if (!error.equals(FurnitureMovementError.NONE)) {
                this.client.sendResponse(
                        new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                return;
            }
        }

        this.client.sendResponse(new RemoveUserItemComposer(item.getGiftAdjustedId()));
        this.client.getUser().getInventory().getItemsComponent().removeUserItem(item.getId());
        polaris.hotel.quests.QuestProgressEvents.progress(
                this.client.getUser(),
                BuildersClubRoomSupport.isTrackedItem(item.getId())
                        ? polaris.hotel.quests.QuestGoalType.PLACE_BUILDERS_CLUB_FURNI
                        : polaris.hotel.quests.QuestGoalType.PLACE_FURNI,
                1);
        item.setFromGift(false);

        if (BuildersClubRoomSupport.isTrackedItem(item.getId())) {
            int trackedUserId = BuildersClubRoomSupport.getTrackedUserId(item.getId());

            if (trackedUserId <= 0) {
                trackedUserId = this.client.getUser().getUserInfo().getId();
            }

            item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);
            BuildersClubRoomSupport.trackPlacedItem(item.getId(), trackedUserId, room.getId());

            BuildersClubRoomSupport.SyncResult syncResult = BuildersClubRoomSupport.syncRoom(room);

            if (syncResult == BuildersClubRoomSupport.SyncResult.LOCKED) {
                BuildersClubRoomSupport.sendRoomLockedBubble(room.getOwnerId());
            } else if (syncResult == BuildersClubRoomSupport.SyncResult.UNLOCKED) {
                BuildersClubRoomSupport.sendRoomUnlockedBubble(room.getOwnerId());
            }

            if (trackedUserId == this.client.getUser().getUserInfo().getId()) {
                this.client.sendResponse(new BuildersClubFurniCountComposer(
                        BuildersClubRoomSupport.getTrackedFurniCount(trackedUserId)));
                this.client.sendResponse(new BuildersClubSubscriptionStatusComposer(this.client.getUser()));
            }
        }
    }
}
