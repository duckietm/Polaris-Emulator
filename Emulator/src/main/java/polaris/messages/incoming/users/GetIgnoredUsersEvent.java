package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.IgnoredUsersComposer;

/** Sends the user's ignored user ids (AIR {@code ?_-02R?/?_-Y2p?}, no fields). */
public class GetIgnoredUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(
                new IgnoredUsersComposer(this.client.getUser().getUserStats().getIgnoredUsers()));
    }
}
