package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksUser;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredClickUserResponseComposer;

/**
 * Official AIR 13 {@code HabboUserDefinedRoomEvents.userSelected} (3122).
 *
 * <p>The client only sends this while {@code WiredEnvironment.hasClickUserWired} is set, and then
 * waits for {@code WiredClickUserResponse} before it decides whether to open the avatar menu.
 */
public class WiredUserSelectedEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomIndex = this.packet.readInt();
        Room room = currentRoom();

        if (room == null || this.client.getUser() == null) {
            return;
        }

        RoomUnit clickingUser = this.client.getUser().getRoomUnit();
        User clickedUser = room.getUserByRoomUnitId(roomIndex);

        if (clickingUser == null || clickedUser == null || clickedUser.getRoomUnit() == null) {
            this.client.sendResponse(new WiredClickUserResponseComposer(roomIndex, true));
            return;
        }

        WiredManager.triggerUserClicksUser(room, clickingUser, clickedUser.getRoomUnit());

        boolean openMenu = !WiredTriggerUserClicksUser.consumeBlockMenuOpen(clickingUser);
        this.client.sendResponse(new WiredClickUserResponseComposer(roomIndex, openMenu));
    }

    @Override
    public int getRatelimit() {
        return 50;
    }
}
