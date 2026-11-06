package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * Header 6014: the arena editor is closed. The manager releases this user's
 * matchmaking lock; once the last editor leaves, games may run again.
 */
public class SnowStormExitEditorEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) {
            return;
        }

        SnowWarManager.getInstance().exitEditor(user);
    }
}
