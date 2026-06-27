package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Client -&gt; server: a user pressed a configured keybind key in the room (header 9311).
 * Payload: one int, the pressed key code. Raises the wired PRESS_KEYBIND trigger via
 * {@link WiredManager#triggerKeybind(Room, com.eu.habbo.habbohotel.rooms.RoomUnit, int)}.
 */
public class PressKeybindEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        int keyCode = this.packet.readInt();

        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getRoomUnit() == null) {
            return;
        }

        WiredManager.triggerKeybind(room, habbo.getRoomUnit(), keyCode);
    }
}
