package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

/**
 * Ignores a user by id (AIR {@code IgnoredUsersManager.ignoreUser}, composer {@code ?_-02R?/?_-Z2I?}). The ignore is
 * stored by id, so the target may be anywhere; the answer names the id so the client can update its list.
 */
public class IgnoreRoomUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        Habbo self = this.client.getHabbo();

        if (userId <= 0 || self == null || userId == self.getHabboInfo().getId()) return;

        if (self.getHabboStats().ignoreUser(this.client, userId)) {
            this.client.sendResponse(new RoomUserIgnoredComposer(userId, RoomUserIgnoredComposer.IGNORED));
            AchievementManager.progressAchievement(
                    self, Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModIgnoreSeen"));
        }
    }
}
