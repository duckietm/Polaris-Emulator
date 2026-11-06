package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedSettingsEvent;

public class SaveGamePrivacySettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        boolean hideOnline = !this.packet.readBoolean();
        boolean blockFollowing = !this.packet.readBoolean();
        boolean blockFriendRequests = !this.packet.readBoolean();

        user.getUserStats().setGamePrivacy(hideOnline, blockFollowing, blockFriendRequests);

        // Optional fourth flag, profile visible to others: older clients stop after three.
        if (this.packet.bytesAvailable() > 0) {
            boolean profileVisible = this.packet.readBoolean();
            user.getUserStats().setProfileHidden(!profileVisible);
        }

        user.getMessenger()
                .connectionChanged(user, user.isOnline(), user.getUserInfo().getCurrentRoom() != null);

        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(user));
    }
}
