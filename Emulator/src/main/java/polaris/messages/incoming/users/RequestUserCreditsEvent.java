package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;

public class RequestUserCreditsEvent extends MessageHandler {
    @Override
    public void handle() {
        this.client.sendResponse(new UserCreditsComposer(this.client.getUser()));
        this.client.sendResponse(new UserCurrencyComposer(this.client.getUser()));
    }
}
