package polaris.messages.incoming.friends;

import java.util.Collections;
import polaris.hotel.messenger.MessengerCategory;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.UpdateFriendComposer;

public class AddFriendCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        UserInfo info = this.client.getUser().getUserInfo();
        String name = FriendCategoryInputGuard.normalizeName(this.packet.readString());
        if (!FriendCategoryInputGuard.isValidName(name)
                || info.getMessengerCategories().size() >= FriendCategoryInputGuard.MAX_CATEGORIES) return;
        if (info.getMessengerCategories().stream()
                .anyMatch(category -> category.getName().equalsIgnoreCase(name))) return;

        info.addMessengerCategory(new MessengerCategory(name, info.getId(), 0));
        this.client.sendResponse(new UpdateFriendComposer(this.client.getUser(), Collections.emptyList(), 0));
    }
}
