package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;

public class DisableMassMentionsCommand extends Command {
    public DisableMassMentionsCommand() {
        super("cmd_disablemassmentions", new String[] {"disablemassmentions", "togglemassmentions"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient == null) return true;
        User user = gameClient.getUser();
        if (user == null || user.getUserStats() == null) return true;

        boolean newState = !user.getUserStats().massMentionsEnabled();
        user.getUserStats().setMassMentionsEnabled(newState);

        user.whisper(
                newState
                        ? "Broadcast mentions (@all / @friends / @room) are now ENABLED for you."
                        : "Broadcast mentions (@all / @friends / @room) are now DISABLED for you. Direct @nick mentions still work.");
        return true;
    }
}
