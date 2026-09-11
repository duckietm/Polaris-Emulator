package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;

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
        Habbo habbo = client.getHabbo();
        if (habbo == null || habbo.getMessenger() == null) return;

        client.sendResponse(new UpdateFriendComposer(
                habbo, habbo.getMessenger().getFriends().values(), 0));
    }
}
