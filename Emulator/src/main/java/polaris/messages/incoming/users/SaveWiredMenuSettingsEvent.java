package polaris.messages.incoming.users;

import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * Official wired menu preferences (1226): the whisper switch in its own column, the rest of the
 * menu preferences as one stored text, sent back in the user settings.
 */
public class SaveWiredMenuSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        UserPreferencePackets.WiredMenuPreferences preferences =
                UserPreferencePackets.parseWiredMenuPreferences(this.packet);

        User user = this.client.getUser();
        boolean wasPlayTestMode = user.getUserStats().isPlayTestMode();

        user.getUserStats().setWiredWhisperDisabled(preferences.wiredWhisperDisabled());
        user.getUserStats().setWiredMenuPreferences(preferences.encode());

        // AIR sends the preferences on every playtest switch; the room applies it at once.
        if (wasPlayTestMode != user.getUserStats().isPlayTestMode()) {
            RoomPlayTestSupport.onPlayTestModeChanged(user);
        }
    }
}
