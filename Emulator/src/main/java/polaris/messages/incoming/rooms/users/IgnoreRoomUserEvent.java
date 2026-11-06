package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

/**
 * Ignores a user by id (AIR {@code IgnoredUsersManager.ignoreUser}, composer {@code ?_-02R?/?_-Z2I?}). The ignore is
 * stored by id, so the target may be anywhere; the answer names the id so the client can update its list.
 */
public class IgnoreRoomUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        User self = this.client.getUser();

        if (userId <= 0 || self == null || userId == self.getUserInfo().getId()) return;

        if (self.getUserStats().ignoreUser(this.client, userId)) {
            this.client.sendResponse(new RoomUserIgnoredComposer(userId, RoomUserIgnoredComposer.IGNORED));
            AchievementManager.progressAchievement(
                    self, Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModIgnoreSeen"));
        }
    }
}
