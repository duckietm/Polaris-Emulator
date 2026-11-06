package polaris.messages.incoming.unknown;

import polaris.Emulator;
import polaris.hotel.achievements.resolution.AchievementResolution;
import polaris.hotel.achievements.resolution.AchievementResolutionManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.events.resolution.NewYearResolutionCompletedComposer;
import polaris.messages.outgoing.events.resolution.NewYearResolutionComposer;
import polaris.messages.outgoing.events.resolution.NewYearResolutionProgressComposer;

/**
 * Opens a resolution furni, and picks a resolution on it.
 *
 * <p>The client sends this with achievement zero to ask what the furni is showing (on click, and
 * again after every level up), and with a real achievement to promise that one. The answer is the
 * picker while the furni carries no promise, the progress while one is open, and the kept-promise
 * window once the owner reached the level. A promise whose clock ran out is dropped, so the furni
 * offers the picker again.
 */
public class RequestResolutionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int achievementId = this.packet.readInt();

        User user = this.client.getUser();
        Room room = this.currentRoom();

        if (user == null || room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null || item.getUserId() != user.getUserInfo().getId()) return;

        AchievementResolutionManager manager = AchievementResolutionManager.getInstance();
        int now = Emulator.getIntUnixTimestamp();
        AchievementResolution resolution = manager.resolution(itemId);

        if (resolution != null && resolution.expired(now)) {
            manager.clear(itemId);
            resolution = null;
        }

        if (resolution == null && achievementId > 0) {
            resolution = manager.promise(user, itemId, achievementId);
        }

        if (resolution == null) {
            this.offerPicker(manager, user, itemId);
            return;
        }

        if (!resolution.completed() && manager.kept(user, resolution)) {
            manager.complete(itemId, now);
            resolution = manager.resolution(itemId);

            // The promise can be gone by the time it is read back (the furni was picked up, or the row dropped):
            // the furni offers the picker again, as it does for any furni without a promise.
            if (resolution == null) {
                this.offerPicker(manager, user, itemId);
                return;
            }
        }

        if (resolution.completed()) {
            this.client.sendResponse(
                    new NewYearResolutionCompletedComposer(item.getBaseItem().getName(), resolution.badgeCode()));
            return;
        }

        int[] progress = manager.progress(user, resolution);

        this.client.sendResponse(new NewYearResolutionProgressComposer(
                itemId,
                resolution.achievementId(),
                resolution.badgeCode(),
                progress[0],
                progress[1],
                resolution.secondsLeft(now)));
    }

    private void offerPicker(AchievementResolutionManager manager, User user, int itemId) {
        this.client.sendResponse(
                new NewYearResolutionComposer(itemId, manager.candidates(user), manager.durationSeconds()));
    }
}
