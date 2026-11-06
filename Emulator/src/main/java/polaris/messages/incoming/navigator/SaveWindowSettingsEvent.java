package polaris.messages.incoming.navigator;

import polaris.hotel.users.UserNavigatorWindowSettings;
import polaris.messages.incoming.MessageHandler;

public class SaveWindowSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        UserNavigatorWindowSettings windowSettings = this.client.getUser().getUserStats().navigatorWindowSettings;

        windowSettings.x = this.packet.readInt();
        windowSettings.y = this.packet.readInt();

        windowSettings.width = this.packet.readInt();
        windowSettings.height = this.packet.readInt();

        boolean openSearches = this.packet.readBoolean();
        windowSettings.openSearches = openSearches;

        this.packet.readInt();
    }
}
