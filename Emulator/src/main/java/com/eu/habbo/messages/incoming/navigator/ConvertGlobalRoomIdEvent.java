package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

/**
 * Resolves the room a link or a navigator jump names and forwards the sender to it. The client sends
 * this whenever the target of a jump is not a plain room id (the web {@code openroom} call and the
 * navigator's own fallback), so the value is either an id in a string or a room name. Only the
 * forward is sent: entering still goes through the normal room entry, with its bans, doorbell and
 * password checks.
 */
public class ConvertGlobalRoomIdEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String target = NavigatorInputGuard.normalizeSearch(this.packet.readString());

        if (target.isEmpty()) return;

        int roomId = asRoomId(target);

        if (roomId <= 0) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoomsWithName(target).stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(target))
                    .findFirst()
                    .orElse(null);

            if (room == null) return;

            roomId = room.getId();
        }

        this.client.sendResponse(new ForwardToRoomComposer(roomId));
    }

    private static int asRoomId(String target) {
        try {
            return Integer.parseInt(target);
        } catch (NumberFormatException notAnId) {
            return 0;
        }
    }
}
