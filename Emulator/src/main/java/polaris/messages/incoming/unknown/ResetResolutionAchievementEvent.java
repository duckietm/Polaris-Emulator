package polaris.messages.incoming.unknown;

import polaris.hotel.achievements.resolution.AchievementResolution;
import polaris.hotel.achievements.resolution.AchievementResolutionManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * "Re-select achievement": the owner drops the promise their furni carries and picks another. The
 * packet names the furni, not the achievement (the client's own parameter name says otherwise, but
 * the official window sends the furni id). A promise that was already kept is left alone: the badge
 * has been awarded and the furni keeps saying so.
 */
public class ResetResolutionAchievementEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        User user = this.client.getUser();
        Room room = this.currentRoom();

        if (user == null || room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null || item.getUserId() != user.getUserInfo().getId()) return;

        AchievementResolutionManager manager = AchievementResolutionManager.getInstance();
        AchievementResolution resolution = manager.resolution(itemId);

        if (resolution == null || resolution.completed()) return;

        manager.clear(itemId);
    }
}
