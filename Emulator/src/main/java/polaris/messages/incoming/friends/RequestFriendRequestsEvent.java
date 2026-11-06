package polaris.messages.incoming.friends;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.LoadFriendRequestsComposer;

public class RequestFriendRequestsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.getUser().getMessenger().reloadFriendRequests(this.client.getUser());
        this.client.sendResponse(new LoadFriendRequestsComposer(this.client.getUser()));
    }
}
