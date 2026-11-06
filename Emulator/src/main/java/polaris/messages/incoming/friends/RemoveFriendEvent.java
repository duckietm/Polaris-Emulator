package polaris.messages.incoming.friends;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import polaris.Emulator;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.RemoveFriendComposer;

public class RemoveFriendEvent extends MessageHandler {
    private static final int MAX_BATCH_SIZE = 100;

    private final IntArrayList removedFriends;

    public RemoveFriendEvent() {
        this.removedFriends = new IntArrayList();
    }

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();
        if (count <= 0 || count > MAX_BATCH_SIZE) return;

        for (int i = 0; i < count; i++) {
            int userId = this.packet.readInt();
            if (userId <= 0) continue;

            this.removedFriends.add(userId);

            Messenger.unfriend(this.client.getUser().getUserInfo().getId(), userId);
            this.client.getUser().getMessenger().removeBuddy(userId);

            User user = Emulator.getGameServer().getGameClientManager().getUser(userId);

            if (user != null) {
                user.getMessenger().removeBuddy(this.client.getUser());
                user.getClient()
                        .sendResponse(new RemoveFriendComposer(
                                this.client.getUser().getUserInfo().getId()));
            }
        }

        this.client.sendResponse(new RemoveFriendComposer(this.removedFriends));
    }
}
