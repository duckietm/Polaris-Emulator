package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.FriendIsTypingComposer;

/**
 * The console tells the friend on the other side that we are writing. The client is edge triggered
 * (one packet when typing starts, one when it stops), so the small rate limit only bites on a burst;
 * a stop dropped by it still clears on the receiver, which forgets the indicator after six seconds.
 * Only a friend who is online is told.
 */
public final class ConsoleTypingEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() {
        int peerId = packet.readInt();
        boolean typing = packet.readBoolean();
        if (!FriendInputGuard.isPositiveId(peerId)) return;

        User self = client.getUser();
        if (self == null || self.getMessenger() == null) return;
        if (self.getMessenger().getFriend(peerId) == null) return;

        User peer = Emulator.getGameEnvironment().getUserManager().getUser(peerId);
        if (peer == null || peer.getClient() == null) return;

        peer.getClient()
                .sendResponse(new FriendIsTypingComposer(self.getUserInfo().getId(), typing));
    }
}
