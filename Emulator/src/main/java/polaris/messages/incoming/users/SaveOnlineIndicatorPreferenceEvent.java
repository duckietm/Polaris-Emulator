package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedSettingsEvent;

/** Official SetOnlineIndicatorPreference (818): which friends trigger the "came online" bubble. */
public class SaveOnlineIndicatorPreferenceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client
                .getUser()
                .getUserStats()
                .setOnlineIndicatorPreference(UserPreferencePackets.parseOnlineIndicatorPreference(this.packet));
        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(this.client.getUser()));
    }
}
