package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.MeMenuSettingsComposer;

public class RequestMeMenuSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new MeMenuSettingsComposer(this.client.getUser()));
    }
}
