package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserProfileComposer;

public class RequestUserProfileEvent extends MessageHandler {
    // An offline profile is read from the database (several queries), as often as the client asked.
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        this.packet.readBoolean(); // UI display hint; profile visibility is enforced by the server response.

        if (!UserInputGuard.isPositiveId(userId)) return;

        User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        if (user != null) this.client.sendResponse(new UserProfileComposer(user, this.client));
        else this.client.sendResponse(new UserProfileComposer(UserManager.getOfflineUserInfo(userId), this.client));
    }
}
