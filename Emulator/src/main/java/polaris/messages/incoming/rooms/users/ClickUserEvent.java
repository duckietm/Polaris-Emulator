package polaris.messages.incoming.rooms.users;

import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksUser;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.InClientLinkComposer;

public class ClickUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        RoomUnit clickingUser = this.client.getUser().getRoomUnit();

        if (clickingUser == null) {
            return;
        }

        int roomUnitId = this.packet.readInt();
        RoomUnit clicked = clickedUnit(room, roomUnitId);

        if (clicked == null) {
            return;
        }

        WiredManager.triggerUserClicksUser(room, clickingUser, clicked);

        if (WiredTriggerUserClicksUser.hasPendingIgnoreLook(clickingUser)) {
            this.client.sendResponse(new InClientLinkComposer("avatar-info/block-rotate"));
        }

        if (WiredTriggerUserClicksUser.consumeBlockMenuOpen(clickingUser)) {
            this.client.sendResponse(new InClientLinkComposer("avatar-info/block-menu"));
        }
    }

    /**
     * The clicked user or bot (not pets): Click User also fires for bots, as in Habbo. Behaviour observed in
     * plus-emulator/PlusEMU d832fa27, reimplemented.
     */
    static RoomUnit clickedUnit(Room room, int roomUnitId) {
        User clickedUser = room.getUserByRoomUnitId(roomUnitId);
        if (clickedUser != null) {
            return clickedUser.getRoomUnit();
        }

        Bot clickedBot = room.getBotByRoomUnitId(roomUnitId);
        return clickedBot != null ? clickedBot.getRoomUnit() : null;
    }
}
