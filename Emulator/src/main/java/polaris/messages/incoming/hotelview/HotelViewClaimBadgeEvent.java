package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewBadgeButtonConfigComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;

/**
 * Claims the badge the hotel view is offering. The badge behind a request code and whether the offer
 * is open are configuration: {@code hotelview.badgereward.<code>.badge} and {@code .enabled}. A
 * closed offer, an unknown code and a badge the visitor already owns all answer the same way the
 * query does, so the element settles into its claimed state instead of hanging.
 */
public class HotelViewClaimBadgeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String requestCode = this.packet.readString();

        User user = this.client.getUser();

        if (user == null || !BadgeRequestGuard.isValidCode(requestCode)) return;

        String key = "hotelview.badgereward." + requestCode;
        String badgeCode = Emulator.getConfig().getValue(key + ".badge", "");

        if (badgeCode.isEmpty() || !Emulator.getConfig().getBoolean(key + ".enabled")) {
            this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(requestCode, false));
            return;
        }

        if (!user.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
            UserBadge badge = BadgesComponent.createBadge(badgeCode, user);

            if (badge != null) {
                this.client.sendResponse(new AddUserBadgeComposer(badge));
            }
        }

        this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(requestCode, true));
    }
}
