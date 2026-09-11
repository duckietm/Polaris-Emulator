package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserHabbiconComposer;

public class RoomUserHabbiconEvent extends MessageHandler {
    private static final int MAX_HABBICON_ID = 1000000;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getRoomUnit() == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int habbiconId = this.packet.readInt();
        if (habbiconId <= 0 || habbiconId > MAX_HABBICON_ID) return;

        if (!habbo.hasPermission(Permission.ACC_NOMUTE)
                && (room.isMuted(habbo) || room.isMuted() && !room.hasRights(habbo))) {
            return;
        }

        HabbiconService habbicons = habbo.getHabbiconService();
        if (!habbo.getHabboStats().allowTalk()
                || !habbicons.use(habbo.getHabboInfo().getId(), habbiconId)) {
            return;
        }

        room.sendComposer(new RoomUserHabbiconComposer(habbo.getRoomUnit(), habbiconId).compose());
        this.client.sendResponse(
                new UserHabbiconsComposer(habbicons.load(habbo.getHabboInfo().getId())));
    }
}
