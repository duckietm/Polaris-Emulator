package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.RemoveFriendComposer;
import gnu.trove.list.array.TIntArrayList;

public class RemoveFriendEvent extends MessageHandler {
    private static final int MAX_BATCH_SIZE = 100;

    private final TIntArrayList removedFriends;

    public RemoveFriendEvent() {
        this.removedFriends = new TIntArrayList();
    }

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();
        if (count <= 0 || count > MAX_BATCH_SIZE) return;

        for (int i = 0; i < count; i++) {
            int habboId = this.packet.readInt();
            if (habboId <= 0) continue;

            this.removedFriends.add(habboId);

            Messenger.unfriend(this.client.getHabbo().getHabboInfo().getId(), habboId);
            this.client.getHabbo().getMessenger().removeBuddy(habboId);

            Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(habboId);

            if (habbo != null) {
                habbo.getMessenger().removeBuddy(this.client.getHabbo());
                habbo.getClient().sendResponse(new RemoveFriendComposer(this.client.getHabbo().getHabboInfo().getId()));
            }
        }

        this.client.sendResponse(new RemoveFriendComposer(this.removedFriends));
    }
}
