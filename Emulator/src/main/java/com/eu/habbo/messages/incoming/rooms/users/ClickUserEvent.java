package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.InClientLinkComposer;

public class ClickUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        RoomUnit clickingUser = this.client.getHabbo().getRoomUnit();

        if (clickingUser == null) {
            return;
        }

        int roomUnitId = this.packet.readInt();
        RoomUnit clicked = clickedUnit(room, roomUnitId);

        if (clicked == null) {
            return;
        }

        WiredManager.triggerUserClicksUser(room, clickingUser, clicked);

        if (WiredTriggerHabboClicksUser.hasPendingIgnoreLook(clickingUser)) {
            this.client.sendResponse(new InClientLinkComposer("avatar-info/block-rotate"));
        }

        if (WiredTriggerHabboClicksUser.consumeBlockMenuOpen(clickingUser)) {
            this.client.sendResponse(new InClientLinkComposer("avatar-info/block-menu"));
        }
    }

    /**
     * The clicked user or bot (not pets): Click User also fires for bots, as in Habbo. Behaviour observed in
     * plus-emulator/PlusEMU d832fa27, reimplemented.
     */
    static RoomUnit clickedUnit(Room room, int roomUnitId) {
        Habbo clickedHabbo = room.getHabboByRoomUnitId(roomUnitId);
        if (clickedHabbo != null) {
            return clickedHabbo.getRoomUnit();
        }

        Bot clickedBot = room.getBotByRoomUnitId(roomUnitId);
        return clickedBot != null ? clickedBot.getRoomUnit() : null;
    }
}
