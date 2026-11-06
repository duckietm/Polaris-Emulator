package polaris.messages.incoming.discord;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.discord.DiscordPreferencesComposer;

/**
 * Official {@code DiscordSettingsController.initComponent()} (header 1055): the client asks for
 * the stored Discord Rich Presence preferences when the component comes up.
 */
public class GetDiscordPreferencesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        this.client.sendResponse(new DiscordPreferencesComposer(
                this.client.getUser().getUserStats().getDiscordPreferences()));
    }
}
