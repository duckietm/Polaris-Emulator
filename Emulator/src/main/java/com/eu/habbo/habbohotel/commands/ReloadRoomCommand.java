package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReloadRoomCommand extends Command {
    public ReloadRoomCommand() {
        super(
                "cmd_reload_room",
                Emulator.getTexts().getValue("commands.keys.cmd_reload_room").split(";"));
    }

    static final long COOLDOWN_MILLIS = 30_000L;

    // When each room was last reloaded: a reload sends everyone back in and resets games and wired.
    private static final Map<Integer, Long> LAST_RELOAD = new ConcurrentHashMap<>();

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo issuer = gameClient.getHabbo();
        Room current = issuer.getHabboInfo().getCurrentRoom();

        // The permission's "with rights" level let anyone holding rights - group members included -
        // reload the room in a loop; only its owner (or staff who own any room) may, and not too often.
        if (current == null || !current.isOwner(issuer) || !claimReload(current.getId(), System.currentTimeMillis())) {
            issuer.whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            return true;
        }

        Emulator.getThreading()
                .run(
                        () -> {
                            Room room = issuer.getHabboInfo().getCurrentRoom();
                            if (room != null) {
                                int roomId = room.getId();
                                Collection<Habbo> habbos = new ArrayList<>(room.getHabbos());
                                Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);
                                Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
                                ServerMessage message = new ForwardToRoomComposer(roomId).compose();
                                for (Habbo habbo : habbos) {
                                    habbo.getClient().sendResponse(message);
                                }
                            }
                        },
                        100);

        return true;
    }

    /** Takes the room's reload slot, unless it was reloaded within the cooldown. */
    static boolean claimReload(int roomId, long now) {
        boolean[] claimed = {false};
        LAST_RELOAD.compute(roomId, (id, last) -> {
            if (last != null && now - last < COOLDOWN_MILLIS) {
                return last;
            }
            claimed[0] = true;
            return now;
        });
        return claimed[0];
    }
}
