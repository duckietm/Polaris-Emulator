package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserSavedSettingsEvent;

public class SaveGamePrivacySettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        boolean hideOnline = !this.packet.readBoolean();
        boolean blockFollowing = !this.packet.readBoolean();
        boolean blockFriendRequests = !this.packet.readBoolean();

        habbo.getHabboStats().setGamePrivacy(hideOnline, blockFollowing, blockFriendRequests);

        // Optional fourth flag, profile visible to others: older clients stop after three.
        if (this.packet.bytesAvailable() > 0) {
            boolean profileVisible = this.packet.readBoolean();
            habbo.getHabboStats().setProfileHidden(!profileVisible);
        }

        habbo.getMessenger()
                .connectionChanged(habbo, habbo.isOnline(), habbo.getHabboInfo().getCurrentRoom() != null);

        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(habbo));
    }
}
