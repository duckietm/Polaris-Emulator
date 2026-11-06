package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

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
        User issuer = gameClient.getUser();
        Room current = issuer.getUserInfo().getCurrentRoom();

        // The permission's "with rights" level let anyone holding rights - group members included -
        // reload the room in a loop; only its owner (or staff who own any room) may, and not too often.
        if (current == null || !current.isOwner(issuer) || !claimReload(current.getId(), System.currentTimeMillis())) {
            issuer.whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            return true;
        }

        Emulator.getThreading()
                .run(
                        () -> {
                            Room room = issuer.getUserInfo().getCurrentRoom();
                            if (room != null) {
                                int roomId = room.getId();
                                Collection<User> users = new ArrayList<>(room.getUsers());
                                Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);
                                Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
                                ServerMessage message = new ForwardToRoomComposer(roomId).compose();
                                for (User user : users) {
                                    user.getClient().sendResponse(message);
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
