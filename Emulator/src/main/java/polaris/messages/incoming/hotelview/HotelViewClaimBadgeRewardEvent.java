package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.AddUserBadgeComposer;

public class HotelViewClaimBadgeRewardEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String request = this.packet.readString();

        if (Emulator.getConfig().getBoolean("hotelview.badgereward." + request + ".enabled")) {
            String badgeCode = Emulator.getConfig().getValue("hotelview.badgereward." + request + "badge");

            if (!badgeCode.isEmpty()) {
                if (!this.client.getUser().getInventory().getBadgesComponent().hasBadge(badgeCode)) {
                    UserBadge badge = BadgesComponent.createBadge(badgeCode, this.client.getUser());
                    this.client.sendResponse(new AddUserBadgeComposer(badge));
                }
            }
        }
    }
}
