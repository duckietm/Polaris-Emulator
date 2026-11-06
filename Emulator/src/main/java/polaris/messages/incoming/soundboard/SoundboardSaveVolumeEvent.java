package polaris.messages.incoming.soundboard;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedSettingsEvent;

public class SoundboardSaveVolumeEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) {
            return;
        }

        user.getUserStats().volumeSoundboard = clampVolume(this.packet.readInt());
        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(user));
    }

    static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }
}
