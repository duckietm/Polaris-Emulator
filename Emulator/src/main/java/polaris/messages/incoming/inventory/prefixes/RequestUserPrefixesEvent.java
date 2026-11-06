package polaris.messages.incoming.inventory.prefixes;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.prefixes.UserPrefixesComposer;

public class RequestUserPrefixesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new UserPrefixesComposer(this.client.getUser()));
    }
}
