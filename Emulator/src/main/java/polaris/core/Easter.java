package polaris.core;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.outgoing.rooms.users.RoomUserPetComposer;
import polaris.messages.outgoing.rooms.users.RoomUserRemoveComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.plugin.EventHandler;
import polaris.plugin.events.users.UserSavedMottoEvent;

public class Easter {
    @EventHandler
    public static void onUserChangeMotto(UserSavedMottoEvent event) {
        if (Emulator.getConfig().getBoolean("easter_eggs.enabled") && event.newMotto.equalsIgnoreCase("crickey!")) {
            event.user
                    .getClient()
                    .sendResponse(new RoomUserWhisperComposer(
                            new RoomChatMessage(event.newMotto, event.user, event.user, RoomChatMessageBubbles.ALERT)));

            Room room = event.user.getUserInfo().getCurrentRoom();

            // The motto can be saved outside a room.
            if (room == null) {
                return;
            }

            room.sendComposer(new RoomUserRemoveComposer(event.user.getRoomUnit()).compose());
            room.sendComposer(new RoomUserPetComposer(2, 1, "FFFFFF", event.user).compose());
        }
    }
}
