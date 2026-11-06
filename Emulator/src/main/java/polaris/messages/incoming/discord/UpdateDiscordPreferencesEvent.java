package polaris.messages.incoming.discord;

import polaris.hotel.users.DiscordPreferences;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.discord.DiscordPreferencesComposer;

/**
 * Official {@code DiscordSettingsController.updatePreferences} (header 2774):
 * {@code (preferenceGlobalVersion, showUser, shareActivity, hideInHiddenRooms, allowJoining)}.
 */
public class UpdateDiscordPreferencesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int version = this.packet.readInt();
        boolean showUser = this.packet.readBoolean();
        boolean shareActivity = this.packet.readBoolean();
        boolean hideInHiddenRooms = this.packet.readBoolean();
        boolean allowJoining = this.packet.readBoolean();

        if (this.client.getUser() == null) {
            return;
        }

        DiscordPreferences preferences =
                new DiscordPreferences(version, showUser, shareActivity, hideInHiddenRooms, allowJoining);

        this.client.getUser().getUserStats().setDiscordPreferences(preferences);
        this.client.sendResponse(new DiscordPreferencesComposer(preferences));
    }
}
