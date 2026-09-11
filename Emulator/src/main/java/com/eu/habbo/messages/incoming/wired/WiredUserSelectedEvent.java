package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredClickUserResponseComposer;

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

        if (room == null || this.client.getHabbo() == null) {
            return;
        }

        RoomUnit clickingUser = this.client.getHabbo().getRoomUnit();
        Habbo clickedHabbo = room.getHabboByRoomUnitId(roomIndex);

        if (clickingUser == null || clickedHabbo == null || clickedHabbo.getRoomUnit() == null) {
            this.client.sendResponse(new WiredClickUserResponseComposer(roomIndex, true));
            return;
        }

        WiredManager.triggerUserClicksUser(room, clickingUser, clickedHabbo.getRoomUnit());

        boolean openMenu = !WiredTriggerHabboClicksUser.consumeBlockMenuOpen(clickingUser);
        this.client.sendResponse(new WiredClickUserResponseComposer(roomIndex, openMenu));
    }

    @Override
    public int getRatelimit() {
        return 50;
    }
}
