package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.FurnitureType;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomRightLevels;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;

public class MoveWallItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: the user moves wall furni as a visitor without rights.
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())
                || (!room.hasRights(this.client.getUser())
                        && !this.client.getUser().hasPermission(Permission.ACC_PLACEFURNI)
                        && !(room.getGuildId() > 0
                                && room.getGuildRightLevel(this.client.getUser())
                                        .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS)))) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String wallPosition = this.packet.readString();

        if (!RoomItemInputGuard.isPositiveId(itemId) || !RoomItemInputGuard.isValidWallPosition(wallPosition)) return;

        UserItem item = room.getUserItem(itemId);

        // A floor furni has no wall position; it moves between tiles (RotateMoveItemEvent).
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.WALL) return;

        boolean moved = !wallPosition.equals(item.getWallPosition());
        item.setWallPosition(wallPosition);
        item.needsUpdate(true);
        room.updateItem(item);
        if (moved) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    this.client.getUser(), polaris.hotel.quests.QuestGoalType.MOVE_FURNI, 1);
        }
    }
}
