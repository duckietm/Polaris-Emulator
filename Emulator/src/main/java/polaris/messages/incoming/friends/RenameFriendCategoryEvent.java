package polaris.messages.incoming.friends;

import java.util.Collections;
import polaris.hotel.messenger.MessengerCategory;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.UpdateFriendComposer;

public class RenameFriendCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        UserInfo info = this.client.getUser().getUserInfo();
        int categoryId = this.packet.readInt();
        String name = FriendCategoryInputGuard.normalizeName(this.packet.readString());
        if (!FriendInputGuard.isPositiveId(categoryId)) return;
        MessengerCategory category = info.getMessengerCategory(categoryId);
        if (category == null || !FriendCategoryInputGuard.isValidName(name)) return;
        if (info.getMessengerCategories().stream()
                .anyMatch(item -> item.getId() != categoryId && item.getName().equalsIgnoreCase(name))) return;
        if (!info.renameMessengerCategory(category, name)) return;

        this.client.sendResponse(new UpdateFriendComposer(this.client.getUser(), Collections.emptyList(), 0));
    }
}
