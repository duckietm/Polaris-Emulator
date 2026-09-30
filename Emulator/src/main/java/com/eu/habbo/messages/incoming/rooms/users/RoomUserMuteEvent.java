package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.MutedWhisperComposer;

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

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || room.getId() != roomId) {
            return;
        }

        if (room.hasRights(this.client.getHabbo())
                || this.client.getHabbo().hasPermission("cmd_mute")
                || this.client.getHabbo().hasPermission(Permission.ACC_AMBASSADOR)) {
            Habbo habbo = room.getHabbo(userId);

            if (habbo != null) {
                if (minutes < MIN_MUTE_MINUTES || minutes > maxMinutes(this.client.getHabbo())) return;

                if (habbo.hasPermission(Permission.ACC_UNKICKABLE)) return;

                room.muteHabbo(habbo, minutes);
                habbo.getClient().sendResponse(new MutedWhisperComposer(minutes * 60));
                AchievementManager.progressAchievement(
                        this.client.getHabbo(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModMuteSeen"));
            }
        }
    }

    static int maxMinutes(Habbo moderator) {
        return moderator.hasPermission("cmd_mute") || moderator.hasPermission(Permission.ACC_AMBASSADOR)
                ? MAX_STAFF_MUTE_MINUTES
                : MAX_MUTE_MINUTES;
    }
}
