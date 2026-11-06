package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedSettingsEvent;

public class SaveUserVolumesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int system = UserInputGuard.clampVolume(this.packet.readInt());
        int furni = UserInputGuard.clampVolume(this.packet.readInt());
        int trax = UserInputGuard.clampVolume(this.packet.readInt());

        this.client.getUser().getUserStats().volumeSystem = system;
        this.client.getUser().getUserStats().volumeFurni = furni;
        this.client.getUser().getUserStats().volumeTrax = trax;

        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(this.client.getUser()));
    }
}
