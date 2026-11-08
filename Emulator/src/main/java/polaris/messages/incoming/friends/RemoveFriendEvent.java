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
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();
        if (count <= 0 || count > MAX_BATCH_SIZE) return;

        for (int i = 0; i < count; i++) {
            int userId = this.packet.readInt();
            if (userId <= 0) continue;
            // Only a current friend is removed: any id used to run the delete and reach that user's client.
            if (this.client.getUser().getMessenger().getFriend(userId) == null) continue;

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

        if (!this.removedFriends.isEmpty()) {
            this.client.sendResponse(new RemoveFriendComposer(this.removedFriends));
        }
    }
}
