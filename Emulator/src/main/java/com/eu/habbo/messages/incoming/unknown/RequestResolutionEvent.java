package com.eu.habbo.messages.incoming.unknown;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolution;
import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolutionManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.events.resolution.NewYearResolutionCompletedComposer;
import com.eu.habbo.messages.outgoing.events.resolution.NewYearResolutionComposer;
import com.eu.habbo.messages.outgoing.events.resolution.NewYearResolutionProgressComposer;

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
        int itemId = this.packet.readInt();
        int achievementId = this.packet.readInt();

        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();

        if (habbo == null || room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null || item.getUserId() != habbo.getHabboInfo().getId()) return;

        AchievementResolutionManager manager = AchievementResolutionManager.getInstance();
        int now = Emulator.getIntUnixTimestamp();
        AchievementResolution resolution = manager.resolution(itemId);

        if (resolution != null && resolution.expired(now)) {
            manager.clear(itemId);
            resolution = null;
        }

        if (resolution == null && achievementId > 0) {
            resolution = manager.promise(habbo, itemId, achievementId);
        }

        if (resolution == null) {
            this.client.sendResponse(
                    new NewYearResolutionComposer(itemId, manager.candidates(habbo), manager.durationSeconds()));
            return;
        }

        if (!resolution.completed() && manager.kept(habbo, resolution)) {
            manager.complete(itemId, now);
            resolution = manager.resolution(itemId);
        }

        if (resolution != null && resolution.completed()) {
            this.client.sendResponse(
                    new NewYearResolutionCompletedComposer(item.getBaseItem().getName(), resolution.badgeCode()));
            return;
        }

        int[] progress = manager.progress(habbo, resolution);

        this.client.sendResponse(new NewYearResolutionProgressComposer(
                itemId,
                resolution.achievementId(),
                resolution.badgeCode(),
                progress[0],
                progress[1],
                resolution.secondsLeft(now)));
    }
}
