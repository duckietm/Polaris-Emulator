package polaris.messages.incoming.navigator;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.NewNavigatorSettingsComposer;

public class RequestNavigatorSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {

        this.client.sendResponse(
                new NewNavigatorSettingsComposer(this.client.getUser().getUserStats().navigatorWindowSettings));
    }
}
