package polaris.messages.incoming.friends;

import polaris.messages.incoming.MessageHandler;

public class RequestOfflineMessagesEvent extends MessageHandler {
    @Override
    public void handle() {
        this.client.getUser().getMessenger().deliverOfflineMessages(this.client.getUser());
    }
}
