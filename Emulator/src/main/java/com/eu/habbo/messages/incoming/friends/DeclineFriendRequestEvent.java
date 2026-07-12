package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.messages.incoming.MessageHandler;

public class DeclineFriendRequestEvent extends MessageHandler {
    private static final int MAX_BATCH_SIZE = 100;

    @Override
    public void handle() throws Exception {
        boolean all = this.packet.readBoolean();

        if (all) {
            this.client.getHabbo().getMessenger().deleteAllFriendRequests(this.client.getHabbo().getHabboInfo().getId());
        } else {
            int count = this.packet.readInt();
            if (count <= 0 || count > MAX_BATCH_SIZE) return;

            for (int i = 0; i < count; i++) {
                int userId = this.packet.readInt();
                if (!FriendInputGuard.isPositiveId(userId)) continue;
                this.client.getHabbo().getMessenger().deleteFriendRequests(userId, this.client.getHabbo().getHabboInfo().getId());
            }
        }
    }
}
