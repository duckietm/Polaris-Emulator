package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * Header 6010: a permitted user (acc_snowwar_arena_build, rank 7 by default) opens the
 * in-game arena editor - either from a running game or straight from the queue.
 * The manager takes the player out of any game/queue, freezes matchmaking and
 * ends every running game (nothing may tick behind the editor), then sends the
 * editor the current arena map to edit. The client switches to edit mode and
 * publishes changes with the save-editor packet (6011).
 */
public class SnowStormEditRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null || !user.hasPermission(SnowWarManager.BUILD_PERMISSION)) {
            return;
        }

        SnowWarManager.getInstance().enterEditor(user);
    }
}
