package polaris.messages.incoming.soundboard;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.soundboard.SoundboardManager;
import polaris.hotel.users.User;
import polaris.messages.outgoing.soundboard.SoundboardSettingsComposer;

public final class SoundboardSettingsSender {
    private SoundboardSettingsSender() {}

    public static void send(User user, Room room) {
        if (user == null || room == null || user.getClient() == null) {
            return;
        }

        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        int rankId = user.getUserInfo().getRank().getId();
        user.getClient()
                .sendResponse(new SoundboardSettingsComposer(
                                room.getSoundboardMode(),
                                manager.getCooldownSecondsForRank(rankId),
                                manager.getSoundsForRank(rankId))
                        .compose());
    }

    public static void sendToRoom(Room room, SoundboardManager manager) {
        if (room == null || manager == null) {
            return;
        }

        for (User recipient : room.getUsers()) {
            if (recipient.getClient() == null) continue;

            int rankId = recipient.getUserInfo().getRank().getId();
            recipient
                    .getClient()
                    .sendResponse(new SoundboardSettingsComposer(
                                    room.getSoundboardMode(),
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
