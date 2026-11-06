package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;

public class DisableMentionsCommand extends Command {
    public DisableMentionsCommand() {
        super("cmd_disablementions", new String[] {"disablementions", "togglementions"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient == null) return true;
        User user = gameClient.getUser();
        if (user == null || user.getUserStats() == null) return true;

        boolean newState = !user.getUserStats().mentionsEnabled();
        user.getUserStats().setMentionsEnabled(newState);

        user.whisper(
                newState
                        ? "@mention notifications are now ENABLED for you."
                        : "@mention notifications are now DISABLED for you. You will not receive direct or broadcast mentions.");
        return true;
    }
}
