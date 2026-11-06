package polaris.messages.incoming.friends;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.RoomInviteComposer;
import polaris.messages.outgoing.friends.RoomInviteErrorComposer;

public class InviteFriendsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserStats().allowTalk()) {
            final int count = this.packet.readInt();
            if (count <= 0 || count > 100) return;

            // Each friend once: the same id repeated sent the same invite once per copy.
            final Set<Integer> userIds = new LinkedHashSet<>();

            for (int i = 0; i < count; i++) {
                userIds.add(this.packet.readInt());
            }

            String message = FriendInputGuard.normalizeMessage(this.packet.readString());

            if (message.isEmpty()) {
                return;
            }

            message = Emulator.getGameEnvironment().getWordFilter().filter(message, this.client.getUser());
            message = FriendInputGuard.normalizeMessage(message);

            // Friends who did not get it: offline, or they have room invites switched off. The
            // sender used to be told nothing at all and had no way of knowing.
            List<MessengerBuddy> missed = new ArrayList<>();
            boolean delivered = false;

            for (int i : userIds) {
                if (!FriendInputGuard.isPositiveId(i)) continue;

                MessengerBuddy buddy =
                        this.client.getUser().getMessenger().getFriends().get(i);

                if (buddy == null) continue;

                User user = Emulator.getGameEnvironment().getUserManager().getUser(i);

                if (user == null || user.getClient() == null || user.getUserStats().blockRoomInvites) {
                    missed.add(buddy);
                    continue;
                }

                user.getClient()
                        .sendResponse(new RoomInviteComposer(
                                this.client.getUser().getUserInfo().getId(), message));
                delivered = true;
            }

            if (delivered) {
                polaris.hotel.quests.QuestProgressEvents.progress(
                        this.client.getUser(), polaris.hotel.quests.QuestGoalType.SEND_MESSENGER_INVITE, 1);
            }

            if (!missed.isEmpty()) {
                this.client.sendResponse(
                        new RoomInviteErrorComposer(RoomInviteErrorComposer.ERROR_RECIPIENT_UNAVAILABLE, missed));
            }
        }
    }
}
