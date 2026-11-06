package polaris.messages.incoming.friends;

import polaris.hotel.messenger.MessengerBuddy;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.UpdateFriendComposer;

public class MoveFriendToCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        int friendId = this.packet.readInt();
        int categoryId = this.packet.readInt();
        if (friendId <= 0 || categoryId < 0) return;
        if (categoryId > 0 && this.client.getUser().getUserInfo().getMessengerCategory(categoryId) == null) return;

        MessengerBuddy buddy = this.client.getUser().getMessenger().getFriend(friendId);
        if (buddy == null || !this.client.getUser().getUserInfo().moveMessengerFriendToCategory(friendId, categoryId))
            return;
        buddy.setCategoryId(categoryId);
        this.client.sendResponse(new UpdateFriendComposer(this.client.getUser(), buddy, 0));
    }
}
