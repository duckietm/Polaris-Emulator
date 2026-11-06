package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewBadgeButtonConfigComposer;

/**
 * The hotel view asks whether a badge on offer has already been claimed, once per refresh of the
 * element that shows it. The badge behind a request code is configured as
 * {@code hotelview.badgereward.<code>.badge}, and {@code .enabled} is what closes the offer.
 */
public class GetIsBadgeRequestFulfilledEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String requestCode = this.packet.readString();

        User user = this.client.getUser();

        if (user == null || !BadgeRequestGuard.isValidCode(requestCode)) return;

        String badge = Emulator.getConfig().getValue("hotelview.badgereward." + requestCode + ".badge", "");
        boolean claimed =
                !badge.isEmpty() && user.getInventory().getBadgesComponent().hasBadge(badge);

        this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(requestCode, claimed));
    }
}
