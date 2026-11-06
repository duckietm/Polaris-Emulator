package polaris.messages.incoming.friends;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.messenger.MessengerCategory;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.UpdateFriendComposer;

public class RemoveFriendCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        UserInfo info = this.client.getUser().getUserInfo();
        int categoryId = this.packet.readInt();
        if (!FriendInputGuard.isPositiveId(categoryId)) return;
        MessengerCategory category = info.getMessengerCategory(categoryId);
        if (category == null) return;

        List<MessengerBuddy> changed = new ArrayList<>();
        for (MessengerBuddy buddy :
                this.client.getUser().getMessenger().getFriends().values()) {
            if (buddy.getCategoryId() != categoryId) continue;
            buddy.setCategoryId(0);
            changed.add(buddy);
        }
        info.deleteMessengerCategory(category);
        this.client.sendResponse(new UpdateFriendComposer(this.client.getUser(), changed, 0));
    }
}
