package polaris.messages.incoming.friends;

import java.util.ArrayList;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.FriendsComposer;
import polaris.messages.outgoing.friends.MessengerInitComposer;

public class RequestInitFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        ArrayList<ServerMessage> messages = new ArrayList<>();
        messages.add(new MessengerInitComposer(this.client.getUser()).compose());
        messages.addAll(FriendsComposer.getMessagesForBuddyList(
                this.client.getUser().getMessenger().getFriends().values()));
        this.client.sendResponses(messages);
    }
}
