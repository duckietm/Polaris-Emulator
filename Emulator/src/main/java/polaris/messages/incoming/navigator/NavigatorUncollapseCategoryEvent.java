package polaris.messages.incoming.navigator;

import polaris.hotel.navigation.DisplayMode;
import polaris.messages.incoming.MessageHandler;

public class NavigatorUncollapseCategoryEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String category = this.packet.readString();
        this.client.getUser().getUserStats().navigatorWindowSettings.setDisplayMode(category, DisplayMode.VISIBLE);
    }
}
