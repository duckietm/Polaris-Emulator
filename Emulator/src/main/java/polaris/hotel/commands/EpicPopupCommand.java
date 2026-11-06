package polaris.hotel.commands;

import java.util.Collection;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.EpicPopups;
import polaris.hotel.users.User;

/**
 * :epicpopup &lt;image&gt; [user]: shows the official epic popup (one framed image with a close button) to the
 * whole hotel, or to one online user. The image is a name in the image library's epic popup folder, an asset URI
 * or an http(s) URL; users who switched staff alerts off do not get it.
 */
public class EpicPopupCommand extends Command {
    public EpicPopupCommand() {
        super(
                "cmd_epic_popup",
                texts().getValue("commands.keys.cmd_epic_popup", "epicpopup;epic")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        TextsManager texts = texts();
        if (params.length < 2) {
            return this.whisper(
                    gameClient, texts.getValue("commands.error.cmd_epic_popup", "Usage: :epicpopup <image> [user]"));
        }

        String assetUri = EpicPopups.assetUri(params[1]);
        if (assetUri == null) {
            return this.whisper(
                    gameClient,
                    texts.getValue("commands.error.cmd_epic_popup.image", "That is not a usable image name or URL."));
        }

        String username = params.length >= 3 ? params[2] : null;
        Collection<User> recipients = EpicPopups.recipients(username);
        if (recipients == null) {
            return this.whisper(
                    gameClient,
                    texts.getValue("commands.error.cmd_epic_popup.offline", "%user% is not online.")
                            .replace("%user%", username));
        }

        int reached = EpicPopups.show(recipients, assetUri);
        return this.whisper(
                gameClient,
                texts.getValue("commands.succes.cmd_epic_popup", "Epic popup shown to %count% users.")
                        .replace("%count%", Integer.toString(reached)));
    }

    private static TextsManager texts() {
        return Emulator.getTexts();
    }

    private boolean whisper(GameClient gameClient, String message) {
        gameClient.getUser().whisper(message, RoomChatMessageBubbles.ALERT);
        return true;
    }
}
