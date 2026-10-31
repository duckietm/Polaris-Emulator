package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserRemoveComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.users.UserSavedMottoEvent;

public class Easter {
    @EventHandler
    public static void onUserChangeMotto(UserSavedMottoEvent event) {
        if (Emulator.getConfig().getBoolean("easter_eggs.enabled") && event.newMotto.equalsIgnoreCase("crickey!")) {
            event.habbo.getClient().sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(event.newMotto, event.habbo, event.habbo, RoomChatMessageBubbles.ALERT)));

            Room room = event.habbo.getHabboInfo().getCurrentRoom();

            // The motto can be saved outside a room.
            if (room == null) {
                return;
            }

            room.sendComposer(new RoomUserRemoveComposer(event.habbo.getRoomUnit()).compose());
            room.sendComposer(new RoomUserPetComposer(2, 1, "FFFFFF", event.habbo).compose());

        }
    }
}
