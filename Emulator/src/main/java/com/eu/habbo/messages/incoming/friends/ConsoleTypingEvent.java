package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendIsTypingComposer;

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

        Habbo self = client.getHabbo();
        if (self == null || self.getMessenger() == null) return;
        if (self.getMessenger().getFriend(peerId) == null) return;

        Habbo peer = Emulator.getGameEnvironment().getHabboManager().getHabbo(peerId);
        if (peer == null || peer.getClient() == null) return;

        peer.getClient()
                .sendResponse(new FriendIsTypingComposer(self.getHabboInfo().getId(), typing));
    }
}
