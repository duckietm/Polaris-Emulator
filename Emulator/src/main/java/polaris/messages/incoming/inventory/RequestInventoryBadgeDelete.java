package polaris.messages.incoming.inventory;

import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryBadgesComposer;
import polaris.messages.outgoing.users.UserBadgesComposer;

public class RequestInventoryBadgeDelete extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        final String badgeCode = this.packet.readString();
        final User user = this.client.getUser();

        if (user == null || badgeCode == null || badgeCode.isEmpty()) return;

        final UserBadge badge = user.getInventory().getBadgesComponent().removeBadge(badgeCode);

        if (badge == null) return;

        BadgesComponent.deleteBadge(user.getUserInfo().getId(), badge.getCode());

        this.client.sendResponse(new InventoryBadgesComposer(user));

        if (user.getUserInfo().getCurrentRoom() != null)
            user.getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new UserBadgesComposer(
                                    user.getInventory().getBadgesComponent().getWearingBadges(),
                                    user.getUserInfo().getId())
                            .compose());
        else
            this.client.sendResponse(new UserBadgesComposer(
                    user.getInventory().getBadgesComponent().getWearingBadges(),
                    user.getUserInfo().getId()));
    }
}
