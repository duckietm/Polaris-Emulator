package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.RoomInviteComposer;

public class InviteFriendsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboStats().allowTalk()) {
            final int count = this.packet.readInt();
            if (count <= 0 || count > 100) return;

            final int[] userIds = new int[count];

            for (int i = 0; i < userIds.length; i++) {
                userIds[i] = this.packet.readInt();
            }

            String message = FriendInputGuard.normalizeMessage(this.packet.readString());

            if (message.isEmpty()) {
                return;
            }

            message = Emulator.getGameEnvironment().getWordFilter().filter(message, this.client.getHabbo());
            message = FriendInputGuard.normalizeMessage(message);

            for (int i : userIds) {
                if (i == 0)
                    continue;

                if (!this.client.getHabbo().getMessenger().getFriends().containsKey(i))
                    continue;

                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(i);

                if (habbo != null) {
                    if (!habbo.getHabboStats().blockRoomInvites) {
                        habbo.getClient().sendResponse(new RoomInviteComposer(this.client.getHabbo().getHabboInfo().getId(), message));
                    }
                }
            }
        }
    }
}
