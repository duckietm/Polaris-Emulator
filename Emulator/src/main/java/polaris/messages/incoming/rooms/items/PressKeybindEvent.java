package polaris.messages.incoming.rooms.items;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;

/**
 * Client -&gt; server: a user pressed a configured keybind key in the room (header 9311).
 * Payload: one int, the pressed key code. Raises the wired PRESS_KEYBIND trigger via
 * {@link WiredManager#triggerKeybind(Room, polaris.hotel.rooms.RoomUnit, int)}.
 */
public class PressKeybindEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        int keyCode = this.packet.readInt();

        User user = this.client.getUser();
        if (user == null) {
            return;
        }

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null || user.getRoomUnit() == null) {
            return;
        }

        WiredManager.triggerKeybind(room, user.getRoomUnit(), keyCode);
    }
}
