package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardSettingsComposer;

public class SoundboardSetEnabledEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = this.currentRoom();
        if (room == null) return;

        // Only the room owner (or staff) may toggle the soundboard for the room.
        boolean isOwner = room.getOwnerId() == habbo.getHabboInfo().getId();
        if (!isOwner && !habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) return;

        boolean enabled = this.packet.readInt() == 1;

        room.setSoundboardEnabled(enabled);
        Emulator.getGameEnvironment().getSoundboardManager().setRoomEnabled(room.getId(), enabled);

        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        for (Habbo recipient : room.getHabbos()) {
            int rankId = recipient.getHabboInfo().getRank().getId();
            recipient.getClient().sendResponse(new SoundboardSettingsComposer(
                            enabled,
                            manager.getCooldownSecondsForRank(rankId),
                            manager.getSoundsForRank(rankId))
                    .compose());
        }
    }
}
