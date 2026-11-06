package polaris.messages.incoming.inventory.nickicons;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;

public class RequestUserNickIconsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new UserNickIconsComposer(this.client.getUser()));
    }
}
