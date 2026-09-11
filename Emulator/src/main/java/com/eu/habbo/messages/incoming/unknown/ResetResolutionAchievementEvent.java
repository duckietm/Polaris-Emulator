package com.eu.habbo.messages.incoming.unknown;

import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolution;
import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolutionManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

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
        int itemId = this.packet.readInt();

        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();

        if (habbo == null || room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null || item.getUserId() != habbo.getHabboInfo().getId()) return;

        AchievementResolutionManager manager = AchievementResolutionManager.getInstance();
        AchievementResolution resolution = manager.resolution(itemId);

        if (resolution == null || resolution.completed()) return;

        manager.clear(itemId);
    }
}
