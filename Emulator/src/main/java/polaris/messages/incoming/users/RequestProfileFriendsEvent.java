package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.users.ProfileVisibility;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.ProfileFriendsComposer;

public class RequestProfileFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (!UserInputGuard.isPositiveId(userId)) return;

        User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        // "Hide my profile" also keeps the relationships from other users.
        if (ProfileVisibility.hidesDetailsFrom(
                this.client.getUser(), userId, ProfileVisibility.isProfileHidden(user, userId))) {
            this.client.sendResponse(new ProfileFriendsComposer(userId));
            return;
        }

        if (user != null) this.client.sendResponse(new ProfileFriendsComposer(user));
        else this.client.sendResponse(new ProfileFriendsComposer(Messenger.getFriends(userId), userId));
    }
}
