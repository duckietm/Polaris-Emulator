package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedSettingsEvent;

/** Official SetChatPreferences (2506): chat mode, bubble width and scroll speed of the user. */
public class SaveChatPreferencesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        UserPreferencePackets.ChatPreferences preferences = UserPreferencePackets.parseChatPreferences(this.packet);
        this.client
                .getUser()
                .getUserStats()
                .setChatPreferences(
                        preferences.chatMode(), preferences.chatBubbleWidth(), preferences.chatScrollSpeed());
        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(this.client.getUser()));
    }
}
