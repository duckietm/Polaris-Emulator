package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.RoomInviteComposer;
import com.eu.habbo.messages.outgoing.friends.RoomInviteErrorComposer;
import java.util.ArrayList;
import java.util.List;

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

            // Friends who did not get it: offline, or they have room invites switched off. The
            // sender used to be told nothing at all and had no way of knowing.
            List<MessengerBuddy> missed = new ArrayList<>();

            for (int i : userIds) {
                if (!FriendInputGuard.isPositiveId(i)) continue;

                MessengerBuddy buddy =
                        this.client.getHabbo().getMessenger().getFriends().get(i);

                if (buddy == null) continue;

                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(i);

                if (habbo == null || habbo.getHabboStats().blockRoomInvites) {
                    missed.add(buddy);
                    continue;
                }

                habbo.getClient()
                        .sendResponse(new RoomInviteComposer(
                                this.client.getHabbo().getHabboInfo().getId(), message));
            }

            if (!missed.isEmpty()) {
                this.client.sendResponse(
                        new RoomInviteErrorComposer(RoomInviteErrorComposer.ERROR_RECIPIENT_UNAVAILABLE, missed));
            }
        }
    }
}
