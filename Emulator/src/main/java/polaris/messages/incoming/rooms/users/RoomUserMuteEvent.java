package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.MutedWhisperComposer;

public class RoomUserMuteEvent extends MessageHandler {
    private static final int MIN_MUTE_MINUTES = 1;
    private static final int MAX_MUTE_MINUTES = 1440;
    // Ambassadors and staff get Flash's longest room mute, 72 hours.
    private static final int MAX_STAFF_MUTE_MINUTES = 4320;

    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();
        int minutes = this.packet.readInt();

        if (!RoomUserInputGuard.isPositiveId(userId) || !RoomUserInputGuard.isPositiveId(roomId)) {
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || room.getId() != roomId) {
            return;
        }

        // The room's mute setting decides which non-staff users may mute.
        if (room.getRightsManager().canMute(this.client.getUser())
                || this.client.getUser().hasPermission("cmd_mute")
                || this.client.getUser().hasPermission(Permission.ACC_AMBASSADOR)) {
            User user = room.getUser(userId);

            if (user != null) {
                if (minutes < MIN_MUTE_MINUTES || minutes > maxMinutes(this.client.getUser())) return;

                if (user.hasPermission(Permission.ACC_UNKICKABLE)) return;

                room.muteUser(user, minutes);
                user.getClient().sendResponse(new MutedWhisperComposer(minutes * 60));
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModMuteSeen"));
            }
        }
    }

    static int maxMinutes(User moderator) {
        return moderator.hasPermission("cmd_mute") || moderator.hasPermission(Permission.ACC_AMBASSADOR)
                ? MAX_STAFF_MUTE_MINUTES
                : MAX_MUTE_MINUTES;
    }
}
