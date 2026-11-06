package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.InteractionStickyPole;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;
import polaris.messages.outgoing.rooms.items.AddWallItemComposer;
import polaris.messages.outgoing.rooms.items.PostItStickyPoleOpenComposer;

public class PostItPlaceEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String location = this.packet.readString();

        if (!RoomItemInputGuard.isPositiveId(itemId) || !RoomItemInputGuard.isValidWallPosition(location)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        // Playtest (the hotel's own rule beyond AIR): post-its stay in the inventory too.
        if (room != null && RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        if (room != null) {
            if (room.hasRights(this.client.getUser())
                    || !room.getRoomSpecialTypes()
                            .getItemsOfType(InteractionStickyPole.class)
                            .isEmpty()) {
                UserItem item =
                        this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);

                if (item instanceof InteractionPostIt) {
                    if (room.getPostItNotes().size() < Room.MAXIMUM_POSTITNOTES) {
                        room.addUserItem(item);
                        item.setExtradata("FFFF33");
                        item.setRoomId(this.client
                                .getUser()
                                .getUserInfo()
                                .getCurrentRoom()
                                .getId());
                        item.setWallPosition(location);
                        item.setUserId(this.client.getUser().getUserInfo().getId());
                        item.needsUpdate(true);
                        room.sendComposer(new AddWallItemComposer(
                                        item,
                                        this.client.getUser().getUserInfo().getUsername())
                                .compose());
                        this.client.getUser().getInventory().getItemsComponent().removeUserItem(item);
                        this.client.sendResponse(new RemoveUserItemComposer(item.getGiftAdjustedId()));
                        item.setFromGift(false);
                        Emulator.getThreading().run(item);

                        if (room.getOwnerId()
                                != this.client.getUser().getUserInfo().getId()) {
                            AchievementManager.progressAchievement(
                                    room.getOwnerId(),
                                    Emulator.getGameEnvironment()
                                            .getAchievementManager()
                                            .getAchievement("NotesReceived"));
                            AchievementManager.progressAchievement(
                                    this.client.getUser().getUserInfo().getId(),
                                    Emulator.getGameEnvironment()
                                            .getAchievementManager()
                                            .getAchievement("NotesLeft"));
                        }

                        if (!room.hasRights(this.client.getUser())) {
                            this.client.sendResponse(new PostItStickyPoleOpenComposer(item));
                        }
                    } else {
                        this.client.sendResponse(new BubbleAlertComposer(
                                BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                                FurnitureMovementError.MAX_STICKIES.errorCode));
                    }
                }
            }
        }
    }
}
