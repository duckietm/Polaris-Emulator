package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserClubComposer;

public class RequestUserClubEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String subscriptionType = this.packet.readString();
        this.client.sendResponse(new UserClubComposer(this.client.getUser(), subscriptionType));
    }
}
