package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedSettingsEvent;

public class SaveIgnoreRoomInvitesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.getUser().getUserStats().blockRoomInvites = this.packet.readBoolean();
        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(this.client.getUser()));
    }
}
