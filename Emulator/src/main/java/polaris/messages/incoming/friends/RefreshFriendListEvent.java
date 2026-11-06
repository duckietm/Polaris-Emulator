package polaris.messages.incoming.friends;

import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.UpdateFriendComposer;

/**
 * The console asks for a full refresh of the friend list (it polls while it is open, and once when it
 * opens). The answer is the same update packet the hotel pushes when a friend connects, carrying every
 * friend so a client that missed a push resynchronises.
 */
public final class RefreshFriendListEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 5000;
    }

    @Override
    public void handle() {
        User user = client.getUser();
        if (user == null || user.getMessenger() == null) return;

        client.sendResponse(
                new UpdateFriendComposer(user, user.getMessenger().getFriends().values(), 0));
    }
}
