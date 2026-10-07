package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardSettingsComposer;

public final class SoundboardSettingsSender {
    private SoundboardSettingsSender() {}

    public static void send(Habbo habbo, Room room) {
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

    public static void sendToRoom(Room room, SoundboardManager manager) {
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

    /**
     * Gives everyone in a room that has the soundboard on the catalog as it is now. The settings
     * packet is otherwise sent only on room entry, on request and when the toggle changes, so a
     * pad added, renamed, reordered or disabled would not show until the player reopened the panel.
     */
    public static void sendToActiveRooms(SoundboardManager manager, RoomManager roomManager) {
        if (manager == null || roomManager == null) {
            return;
        }

        for (Room room : roomManager.getActiveRooms()) {
            if (room == null || !room.isSoundboardEnabled()) continue;

            sendToRoom(room, manager);
        }
    }
}
