package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomBannedUsersComposer;

public class RoomRequestBannedUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        final int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null) return;
        if (!room.hasRights(this.client.getUser()) && !this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER))
            return;

        this.client.sendResponse(new RoomBannedUsersComposer(room));

        if (!room.isOwner(this.client.getUser())) return;
        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModChatFloodFilterSeen"));
        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModChatHearRangeSeen"));
        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModChatScrollSpeedSeen"));
        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModDoorModeSeen"));
    }
}
