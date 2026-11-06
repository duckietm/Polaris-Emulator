package polaris.messages.incoming.friends;

import polaris.messages.incoming.MessageHandler;

public class RequestFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {

        // this.client.sendResponse(new FriendsComposer(this.client.getUser()));

        // this.client.sendResponse(new MessengerInitComposer(this.client.getUser()));
    }
}
