package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingRoomDetailComposer;

public class HousekeepingFindRoomByIdEvent extends HousekeepingHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int roomId = this.packet.readInt();

        if (roomId <= 0) {
            this.client.sendResponse(new HousekeepingRoomDetailComposer(null));
            return;
        }

        // loadRoom covers both the in-memory cache (already-loaded rooms)
        // and the offline path (SELECT * FROM rooms WHERE id=?). Pass
        // false for loadData so we don't pull furni/bots/pets just to
        // render an HK panel summary — getOwnerName / getUserCount work
        // on the pre-loaded skeleton.
        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        this.client.sendResponse(new HousekeepingRoomDetailComposer(room));
    }
}
