package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;
import polaris.messages.outgoing.rooms.RoomPaintComposer;

public class RoomPlacePaintEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        // Playtest (the hotel's own rule beyond AIR): no paint is applied, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || room.hasRights(this.client.getUser())
                || this.client.getUser().hasPermission(Permission.ACC_PLACEFURNI)) {
            int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
            UserItem item =
                    this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);

            if (item == null) {
                this.client.sendResponse(new RemoveUserItemComposer(itemId));
                return;
            }

            if (item.getBaseItem().getName().equals("floor")) {
                room.setFloorPaint(item.getExtradata());

                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoFloor"));
            } else if (item.getBaseItem().getName().equals("wallpaper")) {
                room.setWallPaint(item.getExtradata());

                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoWallpaper"));
            } else if (item.getBaseItem().getName().equals("landscape")) {
                room.setBackgroundPaint(item.getExtradata());

                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoLandscape"));
            } else return;

            this.client.getUser().getInventory().getItemsComponent().removeUserItem(item);
            room.setNeedsUpdate(true);
            room.sendComposer(new RoomPaintComposer(item.getBaseItem().getName(), item.getExtradata()).compose());
            item.needsDelete(true);
            Emulator.getThreading().run(item);
            this.client.sendResponse(new RemoveUserItemComposer(itemId));
        }
    }
}
