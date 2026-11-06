package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserBadgesComposer;

public class RequestWearingBadgesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (!UserInputGuard.isPositiveId(userId)) return;

        User user = Emulator.getGameServer().getGameClientManager().getUser(userId);

        if (user == null
                || user.getUserInfo() == null
                || user.getInventory() == null
                || user.getInventory().getBadgesComponent() == null)
            this.client.sendResponse(new UserBadgesComposer(BadgesComponent.getBadgesOfflineUser(userId), userId));
        else
            this.client.sendResponse(new UserBadgesComposer(
                    user.getInventory().getBadgesComponent().getWearingBadges(),
                    user.getUserInfo().getId()));
    }
}
