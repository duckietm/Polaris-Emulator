package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardSettingsComposer;

final class SoundboardSettingsSender {
    private SoundboardSettingsSender() {}

    static void send(Habbo habbo, Room room) {
        if (habbo == null || room == null || habbo.getClient() == null) {
            return;
        }

        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        int rankId = habbo.getHabboInfo().getRank().getId();
        habbo.getClient()
                .sendResponse(new SoundboardSettingsComposer(
                                room.isSoundboardEnabled(),
                                manager.getCooldownSecondsForRank(rankId),
                                manager.getSoundsForRank(rankId))
                        .compose());
    }

    static void sendToRoom(Room room, SoundboardManager manager) {
        if (room == null || manager == null) {
            return;
        }

        for (Habbo recipient : room.getHabbos()) {
            if (recipient.getClient() == null) continue;

            int rankId = recipient.getHabboInfo().getRank().getId();
            recipient
                    .getClient()
                    .sendResponse(new SoundboardSettingsComposer(
                                    room.isSoundboardEnabled(),
                                    manager.getCooldownSecondsForRank(rankId),
                                    manager.getSoundsForRank(rankId))
                            .compose());
        }
    }
}
