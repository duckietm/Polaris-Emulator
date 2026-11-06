package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.navigation.NavigatorPublicCategory;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomDataComposer;

public class RoomStaffPickEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_STAFF_PICK)) {
            int roomId = this.packet.readInt();

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

            if (room != null) {
                room.setStaffPromotedRoom(!room.isStaffPromotedRoom());
                room.setNeedsUpdate(true);

                NavigatorPublicCategory publicCategory = Emulator.getGameEnvironment()
                        .getNavigatorManager()
                        .publicCategories
                        .get(Emulator.getConfig().getInt("hotel.navigator.staffpicks.categoryid"));
                if (room.isStaffPromotedRoom()) {
                    User user = Emulator.getGameEnvironment().getUserManager().getUser(room.getOwnerId());

                    if (user != null) {
                        AchievementManager.progressAchievement(
                                user,
                                Emulator.getGameEnvironment()
                                        .getAchievementManager()
                                        .getAchievement("Spr"));
                    }

                    if (publicCategory != null) {
                        publicCategory.addRoom(room);
                    }
                } else {
                    if (publicCategory != null) {
                        publicCategory.removeRoom(room);
                    }
                }

                this.client.sendResponse(new RoomDataComposer(room, this.client.getUser(), true, false));
            }
        }
    }
}
